package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CommentDTO;
import com.hiiro.entity.dto.CommentPageDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.CommentMapper;
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
    VideoStatService videoStatService;

    @Resource
    UserFeignApi userFeignApi;

    @Override
    public ResultData<CommentPageDTO> getComments(Long vid, String sort, int page, int pageSize) {
        long start = System.currentTimeMillis();

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
        long total = pagedRoots.getTotal();

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

        // 5. 批量获取用户信息
        List<Long> allUserIds = allComments.stream()
                .flatMap(comment -> Stream.of(comment.getUid(), comment.getToUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<UserDTO> users = allUserIds.isEmpty()
                ? Collections.emptyList()
                : userFeignApi.getBatchUserInfo(allUserIds);

        // 6. 构建评论树
        List<CommentDTO> rootDTOs = buildCommentTreeIterative(allComments, users);

        // 7. 对根评论按原排序方式排序（分页查询时已排序，但构建树后需要保持顺序）
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

        // 8. 子评论按时间升序排列
        rootDTOs.forEach(root -> {
            List<CommentDTO> allReplies = new ArrayList<>();
            collectAllReplies(root.getReplies(), allReplies);
            allReplies.sort(Comparator.comparing(CommentDTO::getCreateDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            root.setReplies(allReplies);
        });

        boolean hasMore = page * pageSize < total;
        CommentPageDTO result = new CommentPageDTO(rootDTOs, total, page, pageSize, hasMore);

        long end = System.currentTimeMillis();
        log.info("获取评论列表耗时：{}ms ", end - start);
        return ResultData.success(result, "获取评论信息成功");
    }

    private List<CommentDTO> buildCommentTreeIterative(List<Comment> comments, List<UserDTO> users) {
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
}
