package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CommentDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.CommentDTOMapper;
import com.hiiro.mapper.CommentMapper;
import com.hiiro.service.CommentService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 评论表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-03-13
 */
@Slf4j
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Resource
    CommentMapper commentMapper;

    @Resource
    VideoStatService videoStatService;

    @Resource
    UserFeignApi userFeignApi;

    /**
     * 获取评论列表
     *
     * @param vid 视频id
     * @return 评论列表
     */
    @Override
    public ResultData<List<CommentDTO>> getComments(Long vid) {
        long start = System.currentTimeMillis();
        // 1. 查询所有未删除的评论
        List<Comment> commentList = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getVid, vid)
                .eq(Comment::getIsDeleted, 0)
                .orderByDesc(Comment::getCreateDate));
        if (commentList.isEmpty()) {
            return ResultData.fail(ResultCodeEnum.COMMENT_NOT_EXIST);
        }
        // 2. 收集所有关联用户ID
        List<Long> allUserIds = commentList.stream()
                .flatMap(comment ->
                        Stream.of(comment.getUid(), comment.getToUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        // 3. 获取用户信息（调用用户服务）
        List<UserDTO> users = allUserIds.isEmpty()
                ? Collections.emptyList()
                : userFeignApi.getBatchUserInfo(allUserIds);

        // 4. 构建评论树
        List<CommentDTO> rootComments = buildCommentTree(commentList, 0, users);

//        Map<Long, UserDTO> userMap = users.stream()
//                .collect(Collectors.toMap(UserDTO::getUid, user -> user));
//        // 4. 转换实体到 DTO 并填充用户信息
//        List<CommentDTO> commentDTOList = commentList.parallelStream()
//                .map(comment -> {
//                    CommentDTO commentDTO = BeanUtil.copyProperties(comment, CommentDTO.class);
//                    // 填充用户信息
//                    Long uid = comment.getUid();
//                    commentDTO.setUser(userMap.get(uid));
//
//                    if (comment.getToUserId() != null) {
//                        Long toUserId = comment.getToUserId();
//                        commentDTO.setToUser(userMap.get(toUserId));
//                    }
//
//                    return commentDTO;
//                })
//                .toList();
        long end = System.currentTimeMillis();
        log.info("获取评论列表耗时：{}ms ", end - start);
        return ResultData.success(rootComments, "获取评论信息成功");
    }

    private List<CommentDTO> buildCommentTree(List<Comment> comments, Integer parentId, List<UserDTO> users) {
        Map<Long, UserDTO> userMap = users.stream()
                .collect(Collectors.toMap(UserDTO::getUid, user -> user));

        return comments.stream()
                .filter(comment -> comment.getParentId().equals(parentId))
                .map(comment -> {
                    CommentDTO dto = BeanUtil.copyProperties(comment, CommentDTO.class);
                    // 填充用户信息
                    dto.setUser(userMap.get(comment.getUid()));
                    if (comment.getToUserId() != null) {
                        dto.setToUser(userMap.get(comment.getToUserId()));
                    }
                    // 递归子评论
                    dto.setReplies(buildCommentTree(comments, comment.getId(), users));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<CommentDTO> sendComment(Comment comment) {
//        Integer rootId = comment.getRootId();
//        // 设置层级关系
//        if (rootId == null || rootId == 0) {
//            comment.setParentId(0);
//            comment.setRootId(0);
//        } else {
//            // 子评论：查询父评论的 rootId
//            Comment parentComment = commentMapper.selectById(rootId);
//            if (parentComment == null) {
//                return ResultData.fail(ResultCodeEnum.COMMENT_NOT_EXIST, "父评论不存在");
//            }
//            comment.setRootId(parentComment.getId()); // 子评论的 rootId = 父评论的 Id
//        }
        if (commentMapper.insert(comment) == 1 && videoStatService.incrementReply(comment.getVid()) == 1) {
            UserDTO userDTO = userFeignApi.getUserByUid(comment.getUid()).getData();
            UserDTO toUserDTO = userFeignApi.getUserByUid(comment.getToUserId()).getData();
            if (Objects.nonNull(userDTO) && Objects.nonNull(toUserDTO)) {
                CommentDTO commentDTO = BeanUtil.copyProperties(comment, CommentDTO.class);
                commentDTO.setUser(userDTO);
                commentDTO.setToUser(toUserDTO);
                return ResultData.success(commentDTO, "发送评论成功");
            }
        }
        return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "发送评论失败");
    }
}
