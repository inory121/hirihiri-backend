package com.hiiro.controller;

import com.aliyun.oss.model.PartETag;
import com.hiiro.entity.ResultCodeEnum;
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
import java.util.Map;
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
    public ResultData<String> initUpload() {
        String uploadId = UUID.randomUUID().toString();
        return ResultData.success(uploadId, "操作成功");
    }

    // 2. 上传分片
    @PostMapping("/chunk")
    public ResultData<String> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileName") String fileName) throws IOException {

        chunkManager.saveChunk(uploadId, chunkNumber, chunk, fileName, totalChunks);
        return ResultData.success("分片上传成功");
    }

    // 3. 合并分片并上传到OSS
//    @PostMapping("/complete")
//    public ResultData<String> completeUpload(
//            @RequestParam("uploadId") String uploadId,
//            @RequestParam("fileName") String fileName) throws IOException {
//        List<File> chunks = chunkManager.getChunks(uploadId);
//        ossUtil.uploadPartsDirectly(fileName, chunks); // 直接上传已接收的分片
//        return ResultData.success("上传完成");
//    }

    @PostMapping("/complete")
    public ResultData<String> completeUpload(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("fileName") String fileName) throws IOException {
        if (!chunkManager.validateChunks(uploadId)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "分片校验失败，请重新上传");
        }
        Map<Integer, PartETag> existingParts = chunkManager.loadChunkStatus(uploadId);
        List<File> chunks = chunkManager.getChunks(uploadId);

        if (!existingParts.isEmpty()) {
            // 断点续传逻辑
            ossUtil.resumeUpload(fileName,
                    chunkManager.mergeChunks(uploadId).getAbsolutePath());
        } else {
            // 全新上传
            ossUtil.uploadPartsDirectly(fileName, chunks);
        }

        chunkManager.cleanTempFiles(uploadId);
        return ResultData.success("上传完成");
    }

//    @PostMapping("/resume-check")
//    public ResultData<ResumeCheckVO> checkResume(@RequestParam("uploadId") String uploadId) {
//        try {
//            // 1. 根据文件名查找最近的上传记录
//            String latestUploadId = chunkManager.findLatestUploadId(uploadId);
//
//            // 2. 没有找到可恢复的上传
//            if (latestUploadId == null) {
//                return ResultData.success(new ResumeCheckVO(false, 0, ""));
//            }
//
//            // 3. 获取上传进度
//            double progress = chunkManager.calculateProgress(latestUploadId);
//            return ResultData.success(
//                    new ResumeCheckVO(true, progress, latestUploadId),
//                    "发现可恢复的上传"
//            );
//        } catch (Exception e) {
//            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "续传检查失败" );
//        }
//    }

    // 续传检查VO
//    @Data
//    @AllArgsConstructor
//    private static class ResumeCheckVO {
//        private boolean resumable;
//        private double progress;
//        private String uploadId;
//    }
}
