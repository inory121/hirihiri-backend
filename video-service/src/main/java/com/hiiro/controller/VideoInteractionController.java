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
     * 收藏/取消收藏
     */
    @Operation(summary = "收藏/取消收藏")
    @PostMapping("/collect/{vid}")
    public ResultData<String> toggleCollect(@PathVariable("vid") Long vid, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return videoInteractionService.toggleCollect(uid, vid);
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
            return ResultData.success(new Boolean[]{false, false, false});
        }
        Long uid = Long.valueOf(uidStr);
        return videoInteractionService.getInteractionStatus(uid, vid);
    }
}