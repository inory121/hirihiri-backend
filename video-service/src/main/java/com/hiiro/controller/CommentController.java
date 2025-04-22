package com.hiiro.controller;

import com.hiiro.entity.Comment;
import com.hiiro.entity.ResultData;
import com.hiiro.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 获取评论列表
     *
     * @param vid 视频id
     * @return 评论列表
     */
    @GetMapping("/get/all/{vid}")
    @Operation(summary = "获取评论列表")
    public ResultData<List<Comment>> getComments(@PathVariable("vid") Long vid) {
        return commentService.getComments(vid);
    }

    /**
     * 发送评论
     *
     * @param comment 评论
     * @return ResultData对象
     */
    @PostMapping("/send")
    @Operation(summary = "添加评论")
    public ResultData<Comment> sendComment(@RequestBody Comment comment) {
        return commentService.sendComment(comment);
    }

}
