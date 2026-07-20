package com.hiiro.controller;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.service.VideoInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 视频互动接口（点赞、投币、收藏）
 *
 * @author hiiro
 * @since 2025-06-23
 */
@Tag(name = "视频互动接口")
@Slf4j
@RestController
@RequestMapping("/api/video/interaction")
public class VideoInteractionController {

    @Resource
    private VideoInteractionService videoInteractionService;

    /**
     * 点赞/取消点赞
     */
    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/like/{vid}")
    public ResultData<String> toggleLike(@PathVariable("vid") Long vid, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return videoInteractionService.toggleLike(uid, vid);
    }

    /**
     * 点踩/取消点踩
     */
    @Operation(summary = "点踩/取消点踩")
    @PostMapping("/dislike/{vid}")
    public ResultData<String> toggleDislike(@PathVariable("vid") Long vid, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return videoInteractionService.toggleDislike(uid, vid);
    }

    /**
     * 投币/取消投币
     */
    @Operation(summary = "投币/取消投币")
    @PostMapping("/coin/{vid}")
    public ResultData<String> toggleCoin(@PathVariable("vid") Long vid, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return videoInteractionService.toggleCoin(uid, vid);
    }

    /**
     * 收藏到指定收藏夹
     */
    @Operation(summary = "收藏到指定收藏夹")
    @PostMapping("/collect/{vid}/folder/{folderId}")
    public ResultData<String> collectToFolder(
            @PathVariable("vid") Long vid,
            @PathVariable("folderId") Long folderId,
            HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return videoInteractionService.collectToFolder(uid, vid, folderId);
    }

    /**
     * 获取用户对视频的互动状态
     */
    @Operation(summary = "获取互动状态")
    @GetMapping("/status/{vid}")
    public ResultData<Boolean[]> getInteractionStatus(@PathVariable("vid") Long vid, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            // 未登录返回全部 false
            return ResultData.success(new Boolean[]{false, false, false, false});
        }
        Long uid = Long.valueOf(uidStr);
        return videoInteractionService.getInteractionStatus(uid, vid);
    }

    /**
     * 获取用户最近投币的视频
     */
    @Operation(summary = "获取最近投币的视频")
    @GetMapping("/recent/coins")
    public ResultData<List<Map<String, Object>>> getRecentCoinVideos(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "uid", required = false) Long targetUid,
            HttpServletRequest request) {
        Long uid;
        if (targetUid != null) {
            // 查询指定用户
            uid = targetUid;
        } else {
            // 查询当前登录用户
            String uidStr = request.getHeader("uid");
            if (!StringUtils.hasText(uidStr)) {
                return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
            }
            uid = Long.valueOf(uidStr);
        }
        return videoInteractionService.getRecentCoinVideos(uid, limit);
    }

    /**
     * 获取用户最近点赞的视频
     */
    @Operation(summary = "获取最近点赞的视频")
    @GetMapping("/recent/likes")
    public ResultData<List<Map<String, Object>>> getRecentLikeVideos(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "uid", required = false) Long targetUid,
            HttpServletRequest request) {
        Long uid;
        if (targetUid != null) {
            // 查询指定用户
            uid = targetUid;
        } else {
            // 查询当前登录用户
            String uidStr = request.getHeader("uid");
            if (!StringUtils.hasText(uidStr)) {
                return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
            }
            uid = Long.valueOf(uidStr);
        }
        return videoInteractionService.getRecentLikeVideos(uid, limit);
    }
}