package com.hiiro.controller;

import com.alibaba.fastjson2.JSON;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.service.VideoService;
import com.hiiro.utils.ChunkUtil;
import com.hiiro.utils.OSSUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Tag(name = "视频上传接口")
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Resource
    ChunkUtil chunkUtil;
    @Resource
    OSSUtil ossUtil;
    @Resource
    private VideoService videoService;

    /**
     * 初始化分片上传
     *
     * @return ResultData对象
     */
    @Operation(summary = "初始化分片上传")
    @PostMapping("/init")
    public ResultData<String> initUpload() {
        String uploadId = UUID.randomUUID().toString();
        return ResultData.success(uploadId, "操作成功");
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

        chunkUtil.saveChunk(uploadId, chunkNumber, chunk, fileName, totalChunks);
        return ResultData.success("分片上传成功");
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
            @RequestPart("videoInfo") String videoInfoJson
    ) {
        // 处理视频文件
        List<File> chunks = chunkUtil.getChunks(uploadId);
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String videoUrl = ossUtil.uploadPartsDirectly(today + "/" + uid + "/video/" + fileName, chunks);
        // 处理封面文件
        String coverUrl = "";
        if (!coverFile.isEmpty()) {
            coverUrl = ossUtil.uploadFile(today + "/" + uid + "/cover/" + coverFile.getOriginalFilename(), coverFile);
        }
        Video video = JSON.parseObject(videoInfoJson, Video.class);
        video.setVideoUrl(videoUrl);
        video.setCoverUrl(coverUrl);
        videoService.saveVideo(uid, video);
        chunkUtil.cleanTempFiles(uploadId);
        return ResultData.success("上传完成");
    }

}
