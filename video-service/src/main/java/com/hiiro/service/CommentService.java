package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CommentDTO;
import com.hiiro.entity.dto.CommentPageDTO;

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
     * @param currentUid 当前登录用户ID（未登录为null）
     * @return 分页评论列表
     */
    ResultData<CommentPageDTO> getComments(Long vid, String sort, int page, int pageSize, Long currentUid);

    /**
     * 根据评论ID获取其所属的完整评论树（根评论+全部回复）
     * 用于从通知跳转时把目标评论所在楼层临时置顶展示
     *
     * @param commentId  评论ID（可以是根评论或子评论）
     * @param currentUid 当前登录用户ID（未登录为null）
     * @return 根评论DTO（含全部回复，扁平化后按时间升序）
     */
    ResultData<CommentDTO> getCommentTree(Long commentId, Long currentUid);

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    ResultData<CommentDTO> sendComment(Comment comment);

    /**
     * 评论点赞/取消点赞
     *
     * @param uid 用户ID
     * @param commentId 评论ID
     * @return 操作结果
     */
    ResultData<String> toggleLike(Long uid, Long commentId);

    /**
     * 评论点踩/取消点踩
     *
     * @param uid 用户ID
     * @param commentId 评论ID
     * @return 操作结果
     */
    ResultData<String> toggleDislike(Long uid, Long commentId);

    /**
     * 删除评论（软删除，仅评论作者本人或视频投稿者可操作）
     */
    ResultData<String> deleteComment(Long uid, Long commentId);

    /**
     * 置顶/取消置顶评论（仅视频投稿者可操作，且只能置顶根评论）
     *
     * @param uid       操作人ID
     * @param commentId 评论ID
     * @param top       true=置顶，false=取消置顶
     * @return 操作结果
     */
    ResultData<String> setCommentTop(Long uid, Long commentId, boolean top);
}
