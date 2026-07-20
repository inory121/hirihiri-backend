package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.CommentDTO;
import com.hiiro.entity.dto.CommentPageDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.CommentDislikeMapper;
import com.hiiro.mapper.CommentLikeMapper;
import com.hiiro.mapper.CommentMapper;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.CommentService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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

        // 根据排序方式添加排序条件
        if ("hot".equalsIgnoreCase(sort == null ? "hot" : sort)) {
            rootWrapper.orderByDesc(Comment::getLike);
        } else {
            rootWrapper.orderByDesc(Comment::getCreateDate);
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
                .orderByAsc(Comment::getCreateDate));

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
        String sortMode = sort == null ? "hot" : sort;
        if ("hot".equalsIgnoreCase(sortMode)) {
            rootDTOs.sort((a, b) -> {
                int likeA = a.getLike() != null ? a.getLike() : 0;
                int likeB = b.getLike() != null ? b.getLike() : 0;
                return Integer.compare(likeB, likeA);
            });
        } else {
            rootDTOs.sort((a, b) -> {
                LocalDateTime timeA = a.getCreateDate();
                LocalDateTime timeB = b.getCreateDate();
                if (timeA == null || timeB == null) {
                    return 0;
                }
                return timeB.compareTo(timeA);
            });
        }

        // 10. 子评论按时间升序排列
        rootDTOs.forEach(root -> {
            List<CommentDTO> allReplies = new ArrayList<>();
            collectAllReplies(root.getReplies(), allReplies);
            allReplies.sort(Comparator.comparing(CommentDTO::getCreateDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            root.setReplies(allReplies);
        });

        boolean hasMore = (long) page * pageSize < rootTotal;
        CommentPageDTO result = new CommentPageDTO(rootDTOs, total, page, pageSize, hasMore);

        long end = System.currentTimeMillis();
        log.info("获取评论列表耗时：{}ms ", end - start);
        return ResultData.success(result, "获取评论信息成功");
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
        if (commentMapper.insert(comment) == 1 && videoStatService.incrementReply(comment.getVid()) == 1) {
            UserDTO userDTO = userFeignApi.getUserByUid(comment.getUid()).getData();
            UserDTO toUserDTO = comment.getToUserId() != null
                    ? userFeignApi.getUserByUid(comment.getToUserId()).getData()
                    : null;
            if (Objects.nonNull(userDTO)) {
                CommentDTO commentDTO = BeanUtil.copyProperties(comment, CommentDTO.class);
                commentDTO.setUser(userDTO);
                commentDTO.setToUser(toUserDTO);
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
}
