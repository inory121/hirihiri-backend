package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultData;

import java.util.List;

/**
 * <p>
 * 评论表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2025-03-13
 */
public interface CommentService extends IService<Comment> {

    /**
     * 获取评论列表
     *
     * @param vid 视频id
     * @return 评论列表
     */
    ResultData<List<Comment>> getComments(Long vid);

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    ResultData<Comment> sendComment(Comment comment);
}
