package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 视频表
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
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "分页获取推荐视频")
    @GetMapping("/recommend")
    public ResultData<List<Map<String, Object>>> getRecommendVideos(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                                    @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return videoService.getRecommendVideos(pageNum, pageSize);
    }

    /**
     * 获取全部视频
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "分页获取全部视频")
    @GetMapping("/all")
    public ResultData<List<Map<String, Object>>> getAllVideos(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                              @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return videoService.getAllVideos(pageNum, pageSize);
    }

    /**
     * 获取视频详情
     *
     * @param vid 视频id
     * @return ResultData对象
     */
    @Operation(summary = "获取视频详情")
    @GetMapping("/{vid}")
    public ResultData<HashMap<String, Object>> getVideoById(@PathVariable("vid") Integer vid) {
        return videoService.getVideoById(vid);
    }

    /**
     * 逻辑删除视频
     *
     * @param video 视频对象
     * @return ResultData对象
     */
    @Operation(summary = "逻辑删除视频")
    @PostMapping("/update/status")
    public ResultData<Video> updateVideoStatus(@RequestBody Video video) {
        return videoService.updateVideoStatus(video.getVid(), video.getStatus());
    }

    /**
     * 搜索视频
     *
     * @param keyword  关键词
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "搜索视频")
    @GetMapping("/search")
    public ResultData<List<Map<String, Object>>> searchVideos(@RequestParam("keyword") String keyword,
                                                              @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                              @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return videoService.searchVideos(keyword, pageNum, pageSize);
    }
}
