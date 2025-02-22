package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.utils.ChunkManager;
import com.hiiro.utils.OSSUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    @Resource
    ChunkManager chunkManager;
    @Resource
    OSSUtil ossUtil;

    // 1. 初始化分片上传（生成唯一 uploadId）
    @PostMapping("/init")
    public ResultData<String> initUpload(@RequestParam("fileName") String fileName) {
        String uploadId = UUID.randomUUID().toString();
        return ResultData.success(uploadId, "操作成功");
    }

    // 2. 上传分片
    @PostMapping("/chunk")
    public ResultData<String> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks) throws IOException {

        File chunkFile = chunkManager.saveChunk(uploadId, chunkNumber, chunk);
        return ResultData.success("分片上传成功");
    }

    // 3. 合并分片并上传到OSS
    @PostMapping("/complete")
    public ResultData<String> completeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName) throws IOException {
        List<File> chunks = chunkManager.getChunks(uploadId);
        ossUtil.uploadPartsDirectly(fileName, chunks); // 直接上传已接收的分片
        return ResultData.success("上传完成");
    }
}
