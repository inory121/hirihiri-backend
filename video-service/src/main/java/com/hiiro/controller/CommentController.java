package com.hiiro.controller;

import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CommentDTO;
import com.hiiro.entity.dto.CommentPageDTO;
import com.hiiro.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 评论表 前端控制器
 * </p>
 *
 * @author hiiro
 * @since 2025-03-13
 */
@RestController
@RequestMapping("/api/comment")
@Tag(name = "评论接口")
public class CommentController {

    @Resource
    private CommentService commentService;

    /**
     * 获取评论列表（分页）
     *
     * @param vid      视频id
     * @param sort     排序方式
     * @param page     页码（从1开始，默认1）
     * @param pageSize 每页大小（默认20）
     * @return 分页评论列表
     */
    @GetMapping("/video/{vid}")
    @Operation(summary = "获取评论列表（分页）")
    public ResultData<CommentPageDTO> getComments(
            @PathVariable("vid") Long vid,
            @RequestParam(name = "sort", defaultValue = "hot") String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        Long currentUid = StringUtils.hasText(uidStr) ? Long.valueOf(uidStr) : null;
        return commentService.getComments(vid, sort, page, pageSize, currentUid);
    }

    /**
     * 根据评论ID获取所属评论树（根评论+全部回复）
     * 用于通知跳转时把目标楼层临时置顶展示
     */
    @GetMapping("/tree/{commentId}")
    @Operation(summary = "根据评论ID获取所属评论树")
    public ResultData<CommentDTO> getCommentTree(@PathVariable("commentId") Long commentId,
                                                 HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        Long currentUid = StringUtils.hasText(uidStr) ? Long.valueOf(uidStr) : null;
        return commentService.getCommentTree(commentId, currentUid);
    }

    /**
     * 获取动态评论列表（分页，按 dynamicId 过滤）
     */
    @GetMapping("/dynamic/{dynamicId}")
    @Operation(summary = "获取动态评论列表（分页）")
    public ResultData<CommentPageDTO> getDynamicComments(
            @PathVariable("dynamicId") Long dynamicId,
            @RequestParam(name = "sort", defaultValue = "hot") String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        Long currentUid = StringUtils.hasText(uidStr) ? Long.valueOf(uidStr) : null;
        return commentService.getDynamicComments(dynamicId, sort, page, pageSize, currentUid);
    }

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    @PostMapping("/send")
    @Operation(summary = "添加评论")
    public ResultData<CommentDTO> sendComment(@RequestBody Comment comment, HttpServletRequest request) {
        String uid = request.getHeader("uid");
        if (!StringUtils.hasText(uid)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "用户未登录");
        }
        comment.setUid(Long.valueOf(uid));
        comment.setId(null);
        return commentService.sendComment(comment);
    }

    /**
     * 评论点赞/取消点赞
     */
    @Operation(summary = "评论点赞/取消点赞")
    @PostMapping("/like/{commentId}")
    public ResultData<String> toggleLike(@PathVariable("commentId") Long commentId, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return commentService.toggleLike(uid, commentId);
    }

    /**
     * 评论点踩/取消点踩
     */
    @Operation(summary = "评论点踩/取消点踩")
    @PostMapping("/dislike/{commentId}")
    public ResultData<String> toggleDislike(@PathVariable("commentId") Long commentId, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return commentService.toggleDislike(uid, commentId);
    }

    /**
     * 删除评论（软删除，仅评论作者本人可操作）
     */
    @Operation(summary = "删除评论")
    @DeleteMapping("/{commentId}")
    public ResultData<String> deleteComment(@PathVariable("commentId") Long commentId, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "用户未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return commentService.deleteComment(uid, commentId);
    }

    /**
     * 置顶/取消置顶评论（仅视频投稿者可操作，且只能置顶根评论）
     */
    @Operation(summary = "置顶/取消置顶评论")
    @PostMapping("/top/{commentId}/{top}")
    public ResultData<String> setCommentTop(@PathVariable("commentId") Long commentId,
                                            @PathVariable("top") Boolean top,
                                            HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "用户未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return commentService.setCommentTop(uid, commentId, top);
    }
}
