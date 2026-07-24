package com.hiiro.controller;

import com.hiiro.entity.RecommendEvent;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.RecommendFeedDTO;
import com.hiiro.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 推荐系统控制器
 *
 * @author hiiro
 * @since 2025-07-23
 */
@RestController
@RequestMapping("/api/recommend")
@Tag(name = "推荐系统")
public class RecommendController {

    @Resource
    private RecommendService recommendService;

    @Operation(summary = "获取推荐流")
    @GetMapping("/feed")
    public ResultData<RecommendFeedDTO> getFeed(
            @Parameter(description = "游标，首次为空")
            @RequestParam(name = "cursor", required = false) String cursor,
            @Parameter(description = "场景：home/related/search，默认home")
            @RequestParam(name = "scene", required = false, defaultValue = "home") String scene,
            @Parameter(description = "每页数量，默认20，最大50")
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestHeader(value = "uid", required = false) String uidStr) {
        Long uid = (uidStr != null && !uidStr.isEmpty()) ? Long.parseLong(uidStr) : null;
        return recommendService.getFeed(cursor, scene, limit, uid);
    }

    @Operation(summary = "上报推荐事件")
    @PostMapping("/events")
    public ResultData<String> reportEvents(
            @RequestBody List<RecommendEvent> events,
            @RequestHeader(value = "uid", required = false) String uidHeader,
            @RequestParam(value = "uid", required = false) String uidParam) {
        String uidStr = (uidHeader != null && !uidHeader.isEmpty()) ? uidHeader : uidParam;
        Long uid = (uidStr != null && !uidStr.isEmpty()) ? Long.parseLong(uidStr) : null;
        return recommendService.reportEvents(events, uid);
    }

    @Operation(summary = "撤销点踩事件")
    @PostMapping("/events/revoke-dislike/{vid}")
    public ResultData<String> revokeDislike(
            @PathVariable("vid") Long vid,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "uid", required = false) String uidStr) {
        Long uid = (uidStr != null && !uidStr.isEmpty()) ? Long.parseLong(uidStr) : null;
        String requestId = body.get("requestId");
        String scene = body.getOrDefault("scene", "home");
        return recommendService.revokeDislike(uid, vid, requestId, scene);
    }

    @Operation(summary = "获取相关推荐视频")
    @GetMapping("/related/{vid}")
    public ResultData<List<Map<String, Object>>> getRelatedVideos(
            @PathVariable("vid") Long vid,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return recommendService.getRelatedVideos(vid, limit);
    }

    @Operation(summary = "标记不感兴趣")
    @PostMapping("/feedback/not-interested/{vid}")
    public ResultData<String> notInterested(
            @PathVariable("vid") Long vid,
            @RequestHeader("uid") String uidStr) {
        Long uid = Long.parseLong(uidStr);
        return recommendService.notInterested(uid, vid);
    }

    @Operation(summary = "屏蔽作者")
    @PostMapping("/feedback/block-author/{authorUid}")
    public ResultData<String> blockAuthor(
            @PathVariable("authorUid") Long authorUid,
            @RequestHeader("uid") String uidStr) {
        Long uid = Long.parseLong(uidStr);
        return recommendService.blockAuthor(uid, authorUid);
    }
}