package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CommentDTO;
import com.hiiro.entity.dto.CommentPageDTO;

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
     * 获取评论列表（分页）
     *
     * @param vid      视频id
     * @param sort     排序方式：hot-最热（根评论点赞数降序），new-最新（创建时间降序）
     * @param page     页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页评论列表
     */
    ResultData<CommentPageDTO> getComments(Long vid, String sort, int page, int pageSize);

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    ResultData<CommentDTO> sendComment(Comment comment);
}
