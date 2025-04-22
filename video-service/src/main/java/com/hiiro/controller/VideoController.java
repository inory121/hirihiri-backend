package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    @GetMapping("/get/recommend")
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
    @GetMapping("/get/all")
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
    @GetMapping("/get/one/{vid}")
    public ResultData<HashMap<String, Object>> getVideoById(@PathVariable("vid") Integer vid) {
        return videoService.getVideoById(vid);
    }

    @Operation(summary = "更改视频状态")
    @PostMapping("/update/status")
    public ResultData<Video> updateVideoStatus(@RequestBody Video video) {
        return videoService.updateVideoStatus(video.getVid(), video.getStatus());
    }
//    /**
//     * 修改视频信息
//     *
//     * @param video 视频对象
//     * @return ResultData对象
//     */
//    @Operation(summary = "修改视频信息")
//    @PostMapping("/update")
//    public ResultData<Video> updateVideo(@RequestBody Video video,@RequestParam("coverFile") MultipartFile coverFile) {
//        return videoService.updateVideo(video,coverFile);
//    }

}
