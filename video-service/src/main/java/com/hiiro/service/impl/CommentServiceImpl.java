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
import com.hiiro.mapper.CommentMapper;
import com.hiiro.service.CommentService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public ResultData<List<CommentDTO>> getComments(Long vid) {
        long start = System.currentTimeMillis();
        List<Comment> commentList = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getVid, vid)
                .eq(Comment::getIsDeleted, 0)
                .orderByDesc(Comment::getCreateDate));
        if (commentList.isEmpty()) {
            return ResultData.fail(ResultCodeEnum.COMMENT_NOT_EXIST);
        }
        List<Long> allUserIds = commentList.stream()
                .flatMap(comment -> Stream.of(comment.getUid(), comment.getToUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<UserDTO> users = allUserIds.isEmpty()
                ? Collections.emptyList()
                : userFeignApi.getBatchUserInfo(allUserIds);

        List<CommentDTO> rootComments = buildCommentTreeIterative(commentList, users);
        long end = System.currentTimeMillis();
        log.info("获取评论列表耗时：{}ms ", end - start);
        return ResultData.success(rootComments, "获取评论信息成功");
    }

    private List<CommentDTO> buildCommentTreeIterative(List<Comment> comments, List<UserDTO> users) {
        Map<Long, UserDTO> userMap = users.stream()
                .collect(Collectors.toMap(UserDTO::getUid, user -> user));

        Map<Integer, CommentDTO> dtoMap = new HashMap<>();
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
                CommentDTO parent = dtoMap.get(parentId);
                if (parent != null) {
                    parent.getReplies().add(dto);
                } else {
                    roots.add(dto);
                }
            }
        }
        return roots;
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
