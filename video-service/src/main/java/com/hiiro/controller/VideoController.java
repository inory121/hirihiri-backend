package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 视频表 前端控制器
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
@Tag(name = "视频接口")
@RestController
@RequestMapping("/api/video")
public class VideoController {

    @Resource
    private VideoService videoService;

    /**
     * 获取推荐视频
     *
     * @return ResultData对象
     */
    @Operation(summary = "获取推荐视频")
    @GetMapping("/get/recommend")
    public ResultData<List<Video>> getRecommendVideos() {
        return videoService.getRecommendVideos();
    }

}
