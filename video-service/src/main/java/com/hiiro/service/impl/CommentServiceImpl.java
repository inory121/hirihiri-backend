package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.mapper.CommentMapper;
import com.hiiro.service.CommentService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 评论表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-03-13
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Resource
    CommentMapper commentMapper;

    @Resource
    VideoStatService videoStatService;

    /**
     * 获取评论列表
     *
     * @param vid 视频id
     * @return 评论列表
     */
    @Override
    public ResultData<List<Comment>> getComments(Long vid) {
        List<Comment> commentList = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getVid, vid).orderByDesc(Comment::getCreateDate));
        if (Objects.isNull(commentList)) {
            return ResultData.fail(ResultCodeEnum.COMMENT_NOT_EXIST, "获取评论列表失败");
        }
        return ResultData.success(commentList);
    }

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<Comment> sendComment(Comment comment) {
        if (commentMapper.insert(comment) == 1 && videoStatService.incrementReply(comment.getVid()) == 1) {
            return ResultData.success(comment, "发送评论成功");
        }
        return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "发送评论失败");
    }
}
