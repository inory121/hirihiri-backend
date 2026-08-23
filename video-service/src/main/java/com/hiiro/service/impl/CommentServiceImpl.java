package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.CommentDTO;
import com.hiiro.entity.dto.CommentPageDTO;
import com.hiiro.entity.dto.MessageNoticeCreateDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.CommentDislikeMapper;
import com.hiiro.mapper.CommentLikeMapper;
import com.hiiro.mapper.CommentMapper;
import com.hiiro.mapper.DynamicMapper;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.CommentService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Resource
    CommentMapper commentMapper;

    @Resource
    CommentLikeMapper commentLikeMapper;

    @Resource
    CommentDislikeMapper commentDislikeMapper;

    @Resource
    VideoMapper videoMapper;

    @Resource
    DynamicMapper dynamicMapper;

    @Resource
    VideoStatService videoStatService;

    @Resource
    UserFeignApi userFeignApi;

    @Override
    public ResultData<CommentPageDTO> getComments(Long vid, String sort, int page, int pageSize, Long currentUid) {
        long start = System.currentTimeMillis();

        Video video = videoMapper.selectById(vid);
        Long videoUpUid = video != null ? video.getUid() : null;

        // 1. 构建根评论查询条件
        LambdaQueryWrapper<Comment> rootWrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getVid, vid)
                .eq(Comment::getIsDeleted, 0)
                .eq(Comment::getRootId, 0); // 只查根评论

        // 根据排序方式添加排序条件（置顶评论始终排最前）
        rootWrapper.orderByDesc(Comment::getIsTop);
        if ("hot".equalsIgnoreCase(sort == null ? "hot" : sort)) {
            rootWrapper.orderByDesc(Comment::getLike);
        } else {
            rootWrapper.orderByDesc(Comment::getCreateTime);
        }

        // 2. 分页查询根评论
        Page<Comment> rootPage = new Page<>(page, pageSize);
        Page<Comment> pagedRoots = commentMapper.selectPage(rootPage, rootWrapper);

        List<Comment> rootComments = pagedRoots.getRecords();
        long rootTotal = pagedRoots.getTotal(); // 根评论总数，用于分页

        // 查询该视频全部评论总数（根评论+回复），用于前端展示
        long total = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getVid, vid)
                .eq(Comment::getIsDeleted, 0));

        if (rootComments.isEmpty()) {
            CommentPageDTO emptyResult = new CommentPageDTO(new ArrayList<>(), total, page, pageSize, false);
            return ResultData.success(emptyResult, "获取评论信息成功");
        }

        // 3. 查询当前页根评论的所有子回复
        List<Long> rootIds = rootComments.stream().map(Comment::getId).collect(Collectors.toList());
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getVid, vid)
                .eq(Comment::getIsDeleted, 0)
                .in(Comment::getRootId, rootIds) // 属于当前页根评论的回复
                .orderByAsc(Comment::getCreateTime));

        // 4. 合并根评论和回复，构建评论树
        List<Comment> allComments = new ArrayList<>(rootComments);
        allComments.addAll(replies);

        List<Long> allCommentIds = allComments.stream().map(Comment::getId).collect(Collectors.toList());

        // 5. 批量获取用户信息
        List<Long> allUserIds = allComments.stream()
                .flatMap(comment -> Stream.of(comment.getUid(), comment.getToUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<UserDTO> users = allUserIds.isEmpty()
                ? Collections.emptyList()
                : userFeignApi.getBatchUserInfo(allUserIds);

        // 6. 查询当前用户点赞/点踩状态
        Set<Long> likedCommentIds = new HashSet<>();
        Set<Long> dislikedCommentIds = new HashSet<>();
        if (currentUid != null) {
            List<CommentLike> likedList = commentLikeMapper.selectList(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUid, currentUid)
                            .in(CommentLike::getCommentId, allCommentIds)
            );
            likedCommentIds = likedList.stream().map(CommentLike::getCommentId).collect(Collectors.toSet());

            List<CommentDislike> dislikedList = commentDislikeMapper.selectList(
                    new LambdaQueryWrapper<CommentDislike>()
                            .eq(CommentDislike::getUid, currentUid)
                            .in(CommentDislike::getCommentId, allCommentIds)
            );
            dislikedCommentIds = dislikedList.stream().map(CommentDislike::getCommentId).collect(Collectors.toSet());
        }

        // 7. 查询UP主点赞的评论
        Set<Long> upLikedCommentIds = new HashSet<>();
        if (videoUpUid != null) {
            List<CommentLike> upLikedList = commentLikeMapper.selectList(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUid, videoUpUid)
                            .in(CommentLike::getCommentId, allCommentIds)
            );
            upLikedCommentIds = upLikedList.stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        }

        // 8. 构建评论树
        List<CommentDTO> rootDTOs = buildCommentTreeIterative(allComments, users, likedCommentIds, dislikedCommentIds, upLikedCommentIds);

        // 9. 对根评论按原排序方式排序（分页查询时已排序，但构建树后需要保持顺序）
        // 置顶评论(isTop=1)不论按最热还是最新，都排在最前
        String sortMode = sort == null ? "hot" : sort;
        rootDTOs.sort((a, b) -> {
            int topA = a.getIsTop() != null ? a.getIsTop() : 0;
            int topB = b.getIsTop() != null ? b.getIsTop() : 0;
            if (topA != topB) {
                return Integer.compare(topB, topA);
            }
            if ("hot".equalsIgnoreCase(sortMode)) {
                int likeA = a.getLike() != null ? a.getLike() : 0;
                int likeB = b.getLike() != null ? b.getLike() : 0;
                return Integer.compare(likeB, likeA);
            } else {
                LocalDateTime timeA = a.getCreateTime();
                LocalDateTime timeB = b.getCreateTime();
                if (timeA == null || timeB == null) {
                    return 0;
                }
                return timeB.compareTo(timeA);
            }
        });

        // 10. 子评论按时间升序排列
        rootDTOs.forEach(root -> {
            List<CommentDTO> allReplies = new ArrayList<>();
            collectAllReplies(root.getReplies(), allReplies);
            allReplies.sort(Comparator.comparing(CommentDTO::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            root.setReplies(allReplies);
        });

        boolean hasMore = (long) page * pageSize < rootTotal;
        CommentPageDTO result = new CommentPageDTO(rootDTOs, total, page, pageSize, hasMore);

        long end = System.currentTimeMillis();
        log.info("获取评论列表耗时：{}ms ", end - start);
        return ResultData.success(result, "获取评论信息成功");
    }

    @Override
    public ResultData<CommentPageDTO> getDynamicComments(Long dynamicId, String sort, int page, int pageSize, Long currentUid) {
        long start = System.currentTimeMillis();

        Dynamic dynamic = dynamicMapper.selectById(dynamicId);
        Long dynamicUpUid = dynamic != null ? dynamic.getUid() : null;

        // 1. 构建根评论查询条件（按 dynamicId 过滤）
        LambdaQueryWrapper<Comment> rootWrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getDynamicId, dynamicId)
                .eq(Comment::getIsDeleted, 0)
                .eq(Comment::getRootId, 0); // 只查根评论

        // 根据排序方式添加排序条件（置顶评论始终排最前）
        rootWrapper.orderByDesc(Comment::getIsTop);
        if ("hot".equalsIgnoreCase(sort == null ? "hot" : sort)) {
            rootWrapper.orderByDesc(Comment::getLike);
        } else {
            rootWrapper.orderByDesc(Comment::getCreateTime);
        }

        // 2. 分页查询根评论
        Page<Comment> rootPage = new Page<>(page, pageSize);
        Page<Comment> pagedRoots = commentMapper.selectPage(rootPage, rootWrapper);

        List<Comment> rootComments = pagedRoots.getRecords();
        long rootTotal = pagedRoots.getTotal(); // 根评论总数，用于分页

        // 查询该动态全部评论总数（根评论+回复），用于前端展示
        long total = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getDynamicId, dynamicId)
                .eq(Comment::getIsDeleted, 0));

        if (rootComments.isEmpty()) {
            CommentPageDTO emptyResult = new CommentPageDTO(new ArrayList<>(), total, page, pageSize, false);
            return ResultData.success(emptyResult, "获取评论信息成功");
        }

        // 3. 查询当前页根评论的所有子回复
        List<Long> rootIds = rootComments.stream().map(Comment::getId).collect(Collectors.toList());
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getDynamicId, dynamicId)
                .eq(Comment::getIsDeleted, 0)
                .in(Comment::getRootId, rootIds) // 属于当前页根评论的回复
                .orderByAsc(Comment::getCreateTime));

        // 4. 合并根评论和回复，构建评论树
        List<Comment> allComments = new ArrayList<>(rootComments);
        allComments.addAll(replies);

        List<Long> allCommentIds = allComments.stream().map(Comment::getId).collect(Collectors.toList());

        // 5. 批量获取用户信息
        List<Long> allUserIds = allComments.stream()
                .flatMap(comment -> Stream.of(comment.getUid(), comment.getToUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, UserDTO> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            try {
                List<UserDTO> users = userFeignApi.getBatchUserInfo(allUserIds);
                if (users != null) {
                    userMap = users.stream().collect(Collectors.toMap(UserDTO::getUid, u -> u, (a, b) -> a));
                }
            } catch (Exception e) {
                log.warn("批量获取评论用户信息失败", e);
            }
        }

        // 6. 批量获取当前用户的点赞/点踩状态
        Set<Long> likedCommentIds = new HashSet<>();
        Set<Long> dislikedCommentIds = new HashSet<>();
        if (currentUid != null && !allCommentIds.isEmpty()) {
            List<CommentLike> myLikes = commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getUid, currentUid)
                    .in(CommentLike::getCommentId, allCommentIds));
            likedCommentIds = myLikes.stream().map(CommentLike::getCommentId).collect(Collectors.toSet());

            List<CommentDislike> myDislikes = commentDislikeMapper.selectList(new LambdaQueryWrapper<CommentDislike>()
                    .eq(CommentDislike::getUid, currentUid)
                    .in(CommentDislike::getCommentId, allCommentIds));
            dislikedCommentIds = myDislikes.stream().map(CommentDislike::getCommentId).collect(Collectors.toSet());
        }

        // 7. 查询动态UP主点赞的评论
        Set<Long> upLikedCommentIds = new HashSet<>();
        if (dynamicUpUid != null) {
            List<CommentLike> upLikedList = commentLikeMapper.selectList(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUid, dynamicUpUid)
                            .in(CommentLike::getCommentId, allCommentIds)
            );
            upLikedCommentIds = upLikedList.stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        }

        // 8. 构建评论树
        List<UserDTO> users = new ArrayList<>(userMap.values());
        List<CommentDTO> rootDTOs = buildCommentTreeIterative(allComments, users, likedCommentIds, dislikedCommentIds, upLikedCommentIds);

        // 9. 对根评论按原排序方式排序（分页查询时已排序，但构建树后需要保持顺序）
        String sortMode = sort == null ? "hot" : sort;
        rootDTOs.sort((a, b) -> {
            int topA = a.getIsTop() != null ? a.getIsTop() : 0;
            int topB = b.getIsTop() != null ? b.getIsTop() : 0;
            if (topA != topB) {
                return Integer.compare(topB, topA);
            }
            if ("hot".equalsIgnoreCase(sortMode)) {
                int likeA = a.getLike() != null ? a.getLike() : 0;
                int likeB = b.getLike() != null ? b.getLike() : 0;
                return Integer.compare(likeB, likeA);
            } else {
                LocalDateTime timeA = a.getCreateTime();
                LocalDateTime timeB = b.getCreateTime();
                if (timeA == null || timeB == null) {
                    return 0;
                }
                return timeB.compareTo(timeA);
            }
        });

        // 10. 子评论按时间升序排列
        rootDTOs.forEach(root -> {
            List<CommentDTO> allReplies = new ArrayList<>();
            collectAllReplies(root.getReplies(), allReplies);
            allReplies.sort(Comparator.comparing(CommentDTO::getCreateTime,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            root.setReplies(allReplies);
        });

        boolean hasMore = (long) page * pageSize < rootTotal;
        CommentPageDTO result = new CommentPageDTO(rootDTOs, total, page, pageSize, hasMore);

        long end = System.currentTimeMillis();
        log.info("获取动态评论列表耗时：{}ms ", end - start);
        return ResultData.success(result, "获取评论信息成功");
    }

    @Override
    public ResultData<CommentDTO> getCommentTree(Long commentId, Long currentUid) {
        // 1. 查目标评论
        Comment target = commentMapper.selectById(commentId);
        if (target == null || target.getIsDeleted() == 1) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "评论不存在");
        }

        // 2. 定位根评论（子评论通过 rootId 上溯）
        Long rootId = (target.getRootId() == null || target.getRootId() == 0) ? target.getId() : target.getRootId();
        Comment root = Objects.equals(rootId, target.getId()) ? target : commentMapper.selectById(rootId);
        if (root == null || root.getIsDeleted() == 1) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "评论所在楼层已删除");
        }

        // 3. 查该楼层全部回复
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getRootId, rootId)
                .eq(Comment::getIsDeleted, 0)
                .orderByAsc(Comment::getCreateTime));

        List<Comment> allComments = new ArrayList<>();
        allComments.add(root);
        allComments.addAll(replies);
        List<Long> allCommentIds = allComments.stream().map(Comment::getId).collect(Collectors.toList());

        // 4. 批量获取用户信息
        List<Long> allUserIds = allComments.stream()
                .flatMap(comment -> Stream.of(comment.getUid(), comment.getToUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<UserDTO> users = allUserIds.isEmpty()
                ? Collections.emptyList()
                : userFeignApi.getBatchUserInfo(allUserIds);

        // 5. 当前用户点赞/点踩状态
        Set<Long> likedCommentIds = new HashSet<>();
        Set<Long> dislikedCommentIds = new HashSet<>();
        if (currentUid != null) {
            likedCommentIds = commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUid, currentUid)
                            .in(CommentLike::getCommentId, allCommentIds))
                    .stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
            dislikedCommentIds = commentDislikeMapper.selectList(new LambdaQueryWrapper<CommentDislike>()
                            .eq(CommentDislike::getUid, currentUid)
                            .in(CommentDislike::getCommentId, allCommentIds))
                    .stream().map(CommentDislike::getCommentId).collect(Collectors.toSet());
        }

        // 6. UP主点赞状态
        Set<Long> upLikedCommentIds = new HashSet<>();
        Video video = root.getVid() != null ? videoMapper.selectById(root.getVid()) : null;
        if (video != null && video.getUid() != null) {
            upLikedCommentIds = commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUid, video.getUid())
                            .in(CommentLike::getCommentId, allCommentIds))
                    .stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        }

        // 7. 构建评论树并扁平化回复（与列表接口保持一致的结构）
        List<CommentDTO> rootDTOs = buildCommentTreeIterative(allComments, users, likedCommentIds, dislikedCommentIds, upLikedCommentIds);
        CommentDTO rootDTO = rootDTOs.stream()
                .filter(dto -> Objects.equals(dto.getId(), rootId))
                .findFirst()
                .orElse(null);
        if (rootDTO == null) {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "评论树构建失败");
        }
        List<CommentDTO> flatReplies = new ArrayList<>();
        collectAllReplies(rootDTO.getReplies(), flatReplies);
        flatReplies.sort(Comparator.comparing(CommentDTO::getCreateTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        rootDTO.setReplies(flatReplies);

        return ResultData.success(rootDTO, "获取评论树成功");
    }

    private List<CommentDTO> buildCommentTreeIterative(List<Comment> comments, List<UserDTO> users,
                                                       Set<Long> likedCommentIds, Set<Long> dislikedCommentIds,
                                                       Set<Long> upLikedCommentIds) {
        Map<Long, UserDTO> userMap = users.stream()
                .collect(Collectors.toMap(UserDTO::getUid, user -> user));

        Map<Long, CommentDTO> dtoMap = new HashMap<>();
        List<CommentDTO> roots = new ArrayList<>();

        for (Comment c : comments) {
            CommentDTO dto = BeanUtil.copyProperties(c, CommentDTO.class);
            dto.setUser(userMap.get(c.getUid()));
            if (c.getToUserId() != null) {
                dto.setToUser(userMap.get(c.getToUserId()));
            }
            dto.setLiked(likedCommentIds.contains(c.getId()));
            dto.setDisliked(dislikedCommentIds.contains(c.getId()));
            dto.setUpLiked(upLikedCommentIds.contains(c.getId()));
            // 评论列表/评论树：把正文的 @uid 解析成结构化用户随包返回，前端零请求渲染可点击 @提及
            dto.setMentionUsers(resolveMentionUsers(c.getContent()));
            dto.setReplies(new ArrayList<>());
            dtoMap.put(c.getId(), dto);
        }

        for (Comment c : comments) {
            CommentDTO dto = dtoMap.get(c.getId());
            Integer parentId = c.getParentId();
            if (parentId == null || parentId == 0) {
                roots.add(dto);
            } else {
                CommentDTO parent = dtoMap.get(parentId.longValue());
                if (parent != null) {
                    parent.getReplies().add(dto);
                } else {
                    roots.add(dto);
                }
            }
        }
        return roots;
    }

    /**
     * 递归收集所有层级的回复到 flat 列表
     */
    private void collectAllReplies(List<CommentDTO> replies, List<CommentDTO> result) {
        if (replies == null || replies.isEmpty()) {
            return;
        }
        for (CommentDTO reply : replies) {
            result.add(reply);
            collectAllReplies(reply.getReplies(), result);
            reply.setReplies(null);
        }
    }

    @Transactional
    @Override
    public ResultData<CommentDTO> sendComment(Comment comment) {
        if (comment.getParentId() == null) {
            comment.setParentId(0);
        }
        // 兜底：根据 parentId 反算真正的 rootId，避免前端误传 rootId=0 产生脏数据
        // （如消息中心内联回复 ntc-act 路径硬编码 rootId=0）
        if (comment.getParentId() != null && comment.getParentId() != 0) {
            Comment parent = commentMapper.selectById(comment.getParentId());
            if (parent != null) {
                Long realRootId = (parent.getRootId() == null || parent.getRootId() == 0)
                        ? parent.getId() : parent.getRootId();
                comment.setRootId(realRootId);
            } else {
                // 父评论不存在，按根评论处理
                comment.setRootId(0L);
            }
        } else {
            // 根评论（parentId 为 0 或空）
            comment.setRootId(0L);
        }
        // 兜底：to_user_id 在库中 NOT NULL 且无默认值，必须保证非空。
        // 若前端未传，则按 parentId/rootId 反查出被回复用户的 uid；仍取不到则置 0。
        if (comment.getToUserId() == null) {
            Long derived = null;
            if (comment.getParentId() != null && comment.getParentId() != 0) {
                Comment parent = commentMapper.selectById(comment.getParentId());
                if (parent != null) {
                    derived = parent.getUid();
                }
            }
            if (derived == null && comment.getRootId() != null && comment.getRootId() != 0) {
                Comment root = commentMapper.selectById(comment.getRootId());
                if (root != null) {
                    derived = root.getUid();
                }
            }
            comment.setToUserId(derived != null ? derived : 0L);
        }
        // 视频评论需同步更新视频统计；动态评论(vid为空)仅插入评论本身
        boolean inserted = commentMapper.insert(comment) == 1;
        if (inserted && comment.getVid() != null
                && videoStatService.incrementReply(comment.getVid()) != 1) {
            // 视频统计更新失败，回滚评论插入（仅视频评论才需要强一致）
            commentMapper.deleteById(comment.getId());
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "评论失败，请重试");
        }
        if (!inserted) {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "评论失败，请重试");
        }
        if (inserted) {
            UserDTO userDTO = userFeignApi.getUserByUid(comment.getUid()).getData();
            UserDTO toUserDTO = comment.getToUserId() != null
                    ? userFeignApi.getUserByUid(comment.getToUserId()).getData()
                    : null;
            if (Objects.nonNull(userDTO)) {
                // 生成互动通知（失败不影响评论主流程）
                try {
                    notifyOnComment(comment);
                } catch (Exception e) {
                    log.warn("评论通知生成失败: {}", e.getMessage());
                }
                CommentDTO commentDTO = BeanUtil.copyProperties(comment, CommentDTO.class);
                commentDTO.setUser(userDTO);
                commentDTO.setToUser(toUserDTO);
                commentDTO.setMentionUsers(resolveMentionUsers(comment.getContent()));
                return ResultData.success(commentDTO, "发送评论成功");
            }
        }
        return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "发送评论失败");
    }

    @Override
    @Transactional
    public ResultData<String> toggleLike(Long uid, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getIsDeleted() == 1) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "评论不存在");
        }

        CommentLike existing = commentLikeMapper.selectOne(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getUid, uid)
                        .eq(CommentLike::getCommentId, commentId)
        );

        if (existing != null) {
            commentLikeMapper.delete(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUid, uid)
                            .eq(CommentLike::getCommentId, commentId)
            );
            comment.setLike(Math.max(0, comment.getLike() - 1));
            commentMapper.updateById(comment);
            // 取消点赞同步删除对应的点赞通知（失败不影响取消点赞主流程）
            try {
                userFeignApi.deleteInternalNotice(comment.getUid(), uid, "like", "comment", commentId);
            } catch (Exception e) {
                log.warn("点赞通知删除失败: {}", e.getMessage());
            }
            return ResultData.success("取消点赞");
        } else {
            CommentDislike existingDislike = commentDislikeMapper.selectOne(
                    new LambdaQueryWrapper<CommentDislike>()
                            .eq(CommentDislike::getUid, uid)
                            .eq(CommentDislike::getCommentId, commentId)
            );
            if (existingDislike != null) {
                commentDislikeMapper.delete(
                        new LambdaQueryWrapper<CommentDislike>()
                                .eq(CommentDislike::getUid, uid)
                                .eq(CommentDislike::getCommentId, commentId)
                );
                comment.setDislike(Math.max(0, comment.getDislike() - 1));
            }

            CommentLike commentLike = new CommentLike();
            commentLike.setUid(uid);
            commentLike.setCommentId(commentId);
            commentLike.setCreateTime(LocalDateTime.now());
            commentLikeMapper.insert(commentLike);
            comment.setLike(comment.getLike() + 1);
            commentMapper.updateById(comment);
            // 生成点赞通知（失败不影响点赞主流程）
            try {
                notifyOnLike(uid, comment);
            } catch (Exception e) {
                log.warn("点赞通知生成失败: {}", e.getMessage());
            }
            return ResultData.success("点赞成功");
        }
    }

    @Override
    @Transactional
    public ResultData<String> toggleDislike(Long uid, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getIsDeleted() == 1) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "评论不存在");
        }

        CommentDislike existing = commentDislikeMapper.selectOne(
                new LambdaQueryWrapper<CommentDislike>()
                        .eq(CommentDislike::getUid, uid)
                        .eq(CommentDislike::getCommentId, commentId)
        );

        if (existing != null) {
            commentDislikeMapper.delete(
                    new LambdaQueryWrapper<CommentDislike>()
                            .eq(CommentDislike::getUid, uid)
                            .eq(CommentDislike::getCommentId, commentId)
            );
            comment.setDislike(Math.max(0, comment.getDislike() - 1));
            commentMapper.updateById(comment);
            return ResultData.success("取消点踩");
        } else {
            CommentLike existingLike = commentLikeMapper.selectOne(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUid, uid)
                            .eq(CommentLike::getCommentId, commentId)
            );
            if (existingLike != null) {
                commentLikeMapper.delete(
                        new LambdaQueryWrapper<CommentLike>()
                                .eq(CommentLike::getUid, uid)
                                .eq(CommentLike::getCommentId, commentId)
                );
                comment.setLike(Math.max(0, comment.getLike() - 1));
                // 点踩顶替点赞时，同步撤回之前的点赞通知
                try {
                    userFeignApi.deleteInternalNotice(comment.getUid(), uid, "like", "comment", commentId);
                } catch (Exception e) {
                    log.warn("点赞通知删除失败: {}", e.getMessage());
                }
            }

            CommentDislike commentDislike = new CommentDislike();
            commentDislike.setUid(uid);
            commentDislike.setCommentId(commentId);
            commentDislike.setCreateTime(LocalDateTime.now());
            commentDislikeMapper.insert(commentDislike);
            comment.setDislike(comment.getDislike() + 1);
            commentMapper.updateById(comment);
            return ResultData.success("点踩成功");
        }
    }

    @Override
    public ResultData<String> deleteComment(Long uid, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getIsDeleted() == 1) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "评论不存在");
        }
        // 评论作者本人，或该视频的投稿者（UP主）均可删除
        boolean isAuthor = Objects.equals(comment.getUid(), uid);
        boolean isVideoOwner = false;
        if (!isAuthor && comment.getVid() != null) {
            Video video = videoMapper.selectById(comment.getVid());
            isVideoOwner = video != null && Objects.equals(video.getUid(), uid);
        }
        if (!isAuthor && !isVideoOwner) {
            return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权删除他人评论");
        }

        // 级联删除：根评论时一并软删除所有子评论；子评论只删自身
        List<Long> idsToDelete = new ArrayList<>();
        idsToDelete.add(commentId);
        if (comment.getRootId() != null && comment.getRootId() == 0L) {
            // 根评论：查出所有子评论（rootId = 该评论id）
            List<Comment> children = commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>().eq(Comment::getRootId, commentId));
            for (Comment child : children) {
                if (child.getIsDeleted() != 1) {
                    idsToDelete.add(child.getId());
                }
            }
        }

        // 批量软删除
        for (Long cid : idsToDelete) {
            Comment c = new Comment();
            c.setId(cid);
            c.setIsDeleted((byte) 1);
            commentMapper.updateById(c);
        }

        // 清理所有被删评论的点赞/点踩记录
        if (!idsToDelete.isEmpty()) {
            commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>().in(CommentLike::getCommentId, idsToDelete));
            commentDislikeMapper.delete(new LambdaQueryWrapper<CommentDislike>().in(CommentDislike::getCommentId, idsToDelete));
        }

        // 评论删除后，清理指向这些评论的通知（回复/@/点赞）
        try {
            userFeignApi.deleteNoticeByBizIds(idsToDelete);
        } catch (Exception e) {
            log.warn("删除评论关联通知失败: {}", e.getMessage());
        }

        // 回复数扣减 = 删除的总条数
        videoStatService.decrementReply(comment.getVid(), idsToDelete.size());
        return ResultData.success("删除成功");
    }

    @Override
    public ResultData<String> setCommentTop(Long uid, Long commentId, boolean top) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getIsDeleted() == 1) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "评论不存在");
        }
        // 仅视频投稿者可置顶
        boolean isVideoOwner = false;
        if (comment.getVid() != null) {
            Video video = videoMapper.selectById(comment.getVid());
            isVideoOwner = video != null && Objects.equals(video.getUid(), uid);
        }
        if (!isVideoOwner) {
            return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权置顶他人视频的评论");
        }
        // 仅根评论可置顶
        if (comment.getRootId() != null && comment.getRootId() != 0L) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "仅根评论可置顶");
        }
        // 一个视频只允许一个置顶评论：置顶前先取消该视频下其他评论的置顶
        if (top) {
            new LambdaUpdateChainWrapper<>(commentMapper)
                    .eq(Comment::getVid, comment.getVid())
                    .ne(Comment::getId, commentId)
                    .set(Comment::getIsTop, (byte) 0)
                    .update();
        }
        comment.setIsTop(top ? (byte) 1 : (byte) 0);
        commentMapper.updateById(comment);
        return ResultData.success(top ? "置顶成功" : "已取消置顶");
    }

    // ======================== 互动通知生成 ========================

    private static final Pattern AT_UID_PATTERN = Pattern.compile("@(\\d+)");
    // @<username> 形式：以字母/下划线开头，其后不紧跟 . @ 或单词字符，避免把邮箱地址误判为 @提及
    private static final Pattern AT_USERNAME_PATTERN = Pattern.compile("(?<![A-Za-z0-9])@([a-zA-Z_]\\w*)(?![.\\w@])");

    /**
     * 评论产生的通知：
     * 1) 回复某条评论 -> 通知被回复用户（noticeType=reply, bizType=comment）
     * 2) 视频根评论   -> 通知视频UP主（noticeType=reply, bizType=video）
     * 3) 内容中 @uid  -> 通知被@用户（noticeType=at, bizType=video）
     */
    private void notifyOnComment(Comment comment) {
        if (comment == null || comment.getVid() == null) return;
        Long vid = comment.getVid();
        String extJson = buildVideoExtJson(vid);
        String content = limitText(comment.getContent(), 200);
        // B站式：把正文里的 @uid 解析成结构化用户列表随包返回，前端零请求渲染可点击 @提及
        extJson = enrichExtWithMentions(content, extJson);
        Long selfUid = comment.getUid();

        boolean isReplyToComment = comment.getParentId() != null && comment.getParentId() != 0;
        // 统一补充 ext：是否回复、根评论内容（@我/回复 右侧展示用）、被回复者信息（“回复 @用户名”前缀用）
        JSONObject ext = extJson != null ? JSONObject.parseObject(extJson) : new JSONObject();
        ext.put("isReply", isReplyToComment);
        if (isReplyToComment) {
            // 根评论内容：评论区回复 @我 时右侧展示根评论正文
            if (comment.getRootId() != null) {
                Comment root = commentMapper.selectById(comment.getRootId());
                if (root != null) {
                    ext.put("rootCommentContent", limitText(root.getContent(), 200));
                    // 预解析根评论自身的 @用户，避免前端缩略图二次请求
                    List<JSONObject> rootMentions = resolveMentionUsers(root.getContent());
                    if (!rootMentions.isEmpty()) {
                        ext.put("rootMentionUsers", rootMentions);
                    }
                }
            }
            // 直接回复对象的内容与作者
            Comment replied = commentMapper.selectById(comment.getParentId());
            if (replied != null) {
                ext.put("originContent", limitText(replied.getContent(), 200));
                // 预解析被回复评论自身的 @用户，供前端引用区零请求渲染（避免只存了根评论的 rootMentionUsers 而漏掉直接父评论）
                List<JSONObject> originMentions = resolveMentionUsers(replied.getContent());
                if (!originMentions.isEmpty()) {
                    ext.put("originMentionUsers", originMentions);
                }
                UserDTO repliedUser = userFeignApi.getUserByUid(replied.getUid()).getData();
                if (repliedUser != null) {
                    ext.put("originUsername", repliedUser.getUsername());
                }
            }
        }
        extJson = ext.toJSONString();
        if (isReplyToComment) {
            if (comment.getToUserId() != null && !Objects.equals(comment.getToUserId(), selfUid)) {
                createNotice(comment.getToUserId(), selfUid, "reply", "comment", comment.getId(), content, extJson);
            }
        } else {
            // 视频根评论 -> 通知视频UP主（bizType=video）
            Video video = videoMapper.selectById(vid);
            if (video != null && video.getUid() != null && !Objects.equals(video.getUid(), selfUid)) {
                createNotice(video.getUid(), selfUid, "reply", "video", comment.getId(), content, extJson);
            }
        }

        // @ 通知：解析内容中的 @<uid> 或 @<username>（手打 @用户名 也能触发）
        for (Long atUid : extractMentionUids(comment.getContent())) {
            if (Objects.equals(atUid, selfUid)) continue;
            // 内容中任何 @用户 均生成 at 通知（包括被回复人：他会同时收到 reply 和 at）
            createNotice(atUid, selfUid, "at", "video", comment.getId(), content, extJson);
        }
    }

    /**
     * 给别人的评论点赞 -> 通知评论作者（noticeType=like, bizType=comment）
     */
    private void notifyOnLike(Long uid, Comment comment) {
        if (comment == null || Objects.equals(comment.getUid(), uid)) return;
        String extJson = buildVideoExtJson(comment.getVid());
        extJson = enrichExtWithMentions(limitText(comment.getContent(), 200), extJson);
        createNotice(comment.getUid(), uid, "like", "comment", comment.getId(),
                limitText(comment.getContent(), 200), extJson);
    }

    /**
     * 组装并发送一条内部通知
     */
    private void createNotice(Long receiveUid, Long actorUid, String noticeType,
                              String bizType, Long bizId, String content, String extJson) {
        if (receiveUid == null) return;
        MessageNoticeCreateDTO dto = new MessageNoticeCreateDTO();
        dto.setReceiveUid(receiveUid);
        dto.setActorUid(actorUid);
        dto.setNoticeType(noticeType);
        dto.setBizType(bizType);
        dto.setBizId(bizId);
        dto.setContentSummary(content);
        dto.setExtJson(extJson);
        userFeignApi.createInternalNotice(dto);
    }

    /**
     * 解析正文中的 @&lt;uid&gt;，调用用户服务拿到 uid→昵称/头像 的映射，
     * 以 mentionUsers 数组塞进 extJson。前端据此零请求渲染可点击的 @提及（参考 B站 at_details 设计）。
     * 解析失败不影响主流程，mentionUsers 缺失时前端降级为原有的请求解析逻辑。
     */
    private String enrichExtWithMentions(String content, String extJson) {
        JSONObject ext = extJson != null ? JSONObject.parseObject(extJson) : new JSONObject();
        if (content != null) {
            List<JSONObject> mentionUsers = resolveMentionUsers(content);
            if (!mentionUsers.isEmpty()) {
                ext.put("mentionUsers", mentionUsers);
            }
        }
        return ext.isEmpty() ? null : ext.toJSONString();
    }

    /**
     * 从正文中提取所有被 @ 的用户 uid，兼容 @<uid>（数字）与 @<username>（用户名）两种格式。
     * 用户名形式通过用户服务反查 uid，使手打 @用户名 也能触发 @通知并被结构化解析。
     */
    private Set<Long> extractMentionUids(String content) {
        Set<Long> uids = new HashSet<>();
        if (content == null) return uids;
        Matcher uidMatcher = AT_UID_PATTERN.matcher(content);
        while (uidMatcher.find()) {
            try {
                Long uid = Long.parseLong(uidMatcher.group(1));
                if (uid != null && uid > 0) uids.add(uid);
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher nameMatcher = AT_USERNAME_PATTERN.matcher(content);
        while (nameMatcher.find()) {
            String username = nameMatcher.group(1);
            if (username == null || username.isEmpty()) continue;
            try {
                UserDTO u = userFeignApi.getUserByUsername(username).getData();
                if (u != null && u.getUid() != null) uids.add(u.getUid());
            } catch (Exception e) {
                log.warn("解析 @用户名 失败 username={}: {}", username, e.getMessage());
            }
        }
        return uids;
    }

    /**
     * 解析正文中的 @<uid> / @<username>，调用用户服务拿到 uid→昵称/头像 的映射（与 mentionUsers 同结构）。
     * 供通知正文、根评论正文、被回复评论正文复用，避免前端渲染时二次请求。
     */
    private List<JSONObject> resolveMentionUsers(String content) {
        List<JSONObject> mentionUsers = new ArrayList<>();
        for (Long uid : extractMentionUids(content)) {
            try {
                UserDTO u = userFeignApi.getUserByUid(uid).getData();
                if (u != null) {
                    JSONObject mu = new JSONObject();
                    mu.put("uid", u.getUid());
                    mu.put("username", u.getUsername());
                    if (u.getNickname() != null) mu.put("nickname", u.getNickname());
                    if (u.getAvatar() != null) mu.put("avatar", u.getAvatar());
                    mentionUsers.add(mu);
                }
            } catch (Exception e) {
                log.warn("解析通知 @用户失败 uid={}: {}", uid, e.getMessage());
            }
        }
        return mentionUsers;
    }

    /**
     * 构建扩展 JSON：塞入视频ID（用于跳转）与封面/标题（用于列表直接展示，避免前端二次查视频）
     */
    private String buildVideoExtJson(Long vid) {
        if (vid == null) return null;
        Video video = videoMapper.selectById(vid);
        if (video == null) return null;
        JSONObject ext = new JSONObject();
        ext.put("videoId", vid);
        if (video.getCoverUrl() != null) ext.put("videoCover", video.getCoverUrl());
        if (video.getTitle() != null) ext.put("videoTitle", video.getTitle());
        return ext.toJSONString();
    }

    private String limitText(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
