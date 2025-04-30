package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CommentDTO;

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
    ResultData<List<CommentDTO>> getComments(Long vid);

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    ResultData<CommentDTO> sendComment(Comment comment);
}
