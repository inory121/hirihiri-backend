package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.service.VideoUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "视频上传接口")
@RestController
@RequestMapping("/api/video/upload")
public class VideoUploadController {

    @Resource
    VideoUploadService videoUploadService;

    /**
     * 初始化分片上传
     *
     * @return ResultData对象
     */
    @Operation(summary = "初始化分片上传")
    @PostMapping("/init")
    public ResultData<String> initUpload() {
        return videoUploadService.initUpload();
    }

    /**
     * 上传分片
     *
     * @param chunk       分片文件
     * @param uploadId    上传ID
     * @param chunkNumber 当前分片序号
     * @param totalChunks 总分片数
     * @param fileName    文件名
     * @return ResultData对象
     */
    @Operation(summary = "上传分片")
    @PostMapping("/chunk")
    public ResultData<String> uploadChunk(
            @RequestPart("file") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileName") String fileName) {

        return videoUploadService.uploadChunk(chunk, uploadId, chunkNumber, totalChunks, fileName);
    }

    /**
     * 完成分片上传
     *
     * @param uploadId      上传ID
     * @param fileName      文件名
     * @param uid           用户ID
     * @param coverFile     封面文件
     * @param videoInfoJson 视频信息JSON
     * @return ResultData对象
     */
    @Operation(summary = "完成分片上传")
    @PostMapping("/complete")
    public ResultData<String> completeUpload(
            @RequestPart("uploadId") String uploadId,
            @RequestPart("fileName") String fileName,
            @RequestHeader("uid") String uid,
            @RequestPart("coverFile") MultipartFile coverFile,
            @RequestPart("videoInfo") String videoInfoJson) {

        return videoUploadService.completeUpload(uploadId, fileName, uid, coverFile, videoInfoJson);
    }

    /**
     * 取消上传
     *
     * @param uploadId 上传ID
     * @return ResultData对象
     */
    @Operation(summary = "取消上传")
    @PostMapping("/cancel")
    public ResultData<String> cancelUpload(@RequestPart("uploadId") String uploadId) {
        return videoUploadService.cancelUpload(uploadId);
    }

}
