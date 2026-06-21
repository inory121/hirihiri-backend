package com.hiiro.service.impl;

import com.alibaba.fastjson2.JSON;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.entity.dto.VideoUploadDTO;
import com.hiiro.service.VideoService;
import com.hiiro.service.VideoUploadService;
import com.hiiro.utils.ChunkUtil;
import com.hiiro.utils.FileValidationUtils;
import com.hiiro.utils.OSSUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class VideoUploadServiceImpl implements VideoUploadService {

    @Resource
    ChunkUtil chunkUtil;
    @Resource
    OSSUtil ossUtil;
    @Resource
    VideoService videoService;

    /**
     * 初始化分片上传
     *
     * @return ResultData对象
     */
    @Override
    public ResultData<String> initUpload() {
        return ResultData.success(UUID.randomUUID().toString(), "操作成功");
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
    @Override
    public ResultData<String> uploadChunk(MultipartFile chunk, String uploadId, int chunkNumber, int totalChunks, String fileName) {
        if (chunkNumber == 1){
            try {
                String fileExtension = FileValidationUtils.validateExtension(fileName, "video");
                FileValidationUtils.validateMagicNumber(chunk, fileExtension);
            } catch (IOException | IllegalArgumentException e) {
                log.warn("视频文件校验失败: {}", e.getMessage());
                return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "Σ( ° △ °|||) 上传格式不被支持");
            }
        }
        chunkUtil.saveChunk(uploadId, chunkNumber, chunk, fileName, totalChunks);
        return ResultData.success("分片上传成功");
    }

    /**
     * 生成安全的存储路径
     *
     * @param date             日期（如 "20250503"）
     * @param uid              用户ID
     * @param type             类型（video/cover）
     * @param originalFilename 原始文件名
     * @return 完整路径
     */
    private String generateStoragePath(String date, String uid, String type, String originalFilename) {
//        String safeFileName = sanitizeFileName(originalFilename);
        return String.join("/", date, uid, type, originalFilename);
    }

    /**
     * 安全处理文件名：移除非法字符 + 添加唯一标识
     *
     * @param originalFilename 原始文件名
     * @return 安全文件名
     */
    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        // 提取扩展名
        String fileExtension = "";
        int lastDotIndex = originalFilename.lastIndexOf(".");
        if (lastDotIndex > 0 && lastDotIndex < originalFilename.length() - 1) {
            fileExtension = originalFilename.substring(lastDotIndex);
        } else {
            fileExtension = ".tmp"; // 默认扩展名
        }

        // 构建安全基础名
        String baseName = originalFilename.substring(0, lastDotIndex);
        String safeBaseName = baseName.replaceAll("[^a-zA-Z0-9\\-_]", "_");

        // 添加唯一标识
        return safeBaseName + "_" + UUID.randomUUID() + fileExtension;
    }

    /**
     * 处理封面上传逻辑
     *
     * @param coverFile 封面文件
     * @param date      日期
     * @param uid       用户ID
     * @return 封面URL
     */
    private String handleCoverUpload(MultipartFile coverFile, String date, String uid) throws IOException {
        String originalFilename = coverFile.getOriginalFilename();
        String fileExtension = validateCoverFile(originalFilename, coverFile.getSize());

        // 提取基础名（去掉扩展名）
        String baseName = null;
        if (originalFilename != null) {
            baseName = originalFilename.substring(0, originalFilename.lastIndexOf(".")) + fileExtension;
        }

        // 移除非安全字符（可选）
//        String safeBaseName = baseName.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-_()]", "_");

        // 构建安全文件名：原始基础名 + UUID + 扩展名
//        String safeFileName = baseName + "_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10) + fileExtension;
        FileValidationUtils.validateMagicNumber(coverFile, fileExtension);
        // 构建路径并上传
        String coverPath = generateStoragePath(date, uid, "cover", baseName);
        return ossUtil.uploadFile(coverPath, coverFile);
    }

    /**
     * 验证封面文件合法性
     *
     * @param filename 文件名
     * @param size     文件大小
     */
//    private String validateCoverFile(String filename, long size) {
//        if (filename == null || filename.isBlank()) {
//            throw new IllegalArgumentException("文件名无效");
//        }
//
//        int lastDotIndex = filename.lastIndexOf(".");
//        if (lastDotIndex <= 0 || lastDotIndex == filename.length() - 1) {
//            throw new IllegalArgumentException("文件扩展名无效");
//        }
//
//        String fileExtension = filename.substring(lastDotIndex).toLowerCase();
//
//        List<String> allowedExtensions = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp");
//        if (!allowedExtensions.contains(fileExtension)) {
//            throw new IllegalArgumentException("不支持的文件类型: " + fileExtension);
//        }
//
//        return fileExtension;
//    }
    private String validateCoverFile(String filename, long size) {
        // 调用通用扩展名校验
        String fileExtension = FileValidationUtils.validateExtension(filename, "image");

        // 可选：添加文件大小限制（如 10MB）
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (size > maxSize) {
            throw new IllegalArgumentException("封面文件过大");
        }

        return fileExtension;
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
    @Override
    public ResultData<String> completeUpload(String uploadId, String fileName, String uid, MultipartFile coverFile, String videoInfoJson) {
        List<File> chunks = chunkUtil.getChunks(uploadId);
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        // 1. 上传视频文件
        String videoPath = generateStoragePath(today, uid, "video", fileName);
        String videoUrl;
        try {
            videoUrl = ossUtil.uploadPartsDirectly(videoPath, chunks);
        } catch (Exception e) {
            log.error("视频上传失败: {}", videoPath, e);
            chunkUtil.cleanTempFiles(uploadId);
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "视频上传失败");
        }

        // 2. 上传封面文件（如果存在）
        String coverUrl = "";
        if (!coverFile.isEmpty()) {
            try {
                coverUrl = handleCoverUpload(coverFile, today, uid);
            } catch (Exception e) {
                log.error("封面上传失败: {}", coverFile.getOriginalFilename(), e);
                ossUtil.deleteFile(videoPath); // 删除已上传的视频
                chunkUtil.cleanTempFiles(uploadId);
                return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "封面上传失败");
            }
        }

        // 3. 保存视频信息 — 只接受白名单字段
        VideoUploadDTO uploadDTO = JSON.parseObject(videoInfoJson, VideoUploadDTO.class);
        Video video = new Video();
        video.setTitle(uploadDTO.getTitle());
        video.setDescr(uploadDTO.getDescription());
        video.setMcId(uploadDTO.getMcId());
        video.setScId(uploadDTO.getScId());
        video.setTags(uploadDTO.getTags());
        video.setType(uploadDTO.getType() != null ? uploadDTO.getType() : 1);
        video.setAuth(uploadDTO.getAuth() != null ? uploadDTO.getAuth() : 0);
        video.setVideoUrl(videoUrl);
        video.setCoverUrl(coverUrl);

        if (!videoService.saveVideo(uid, video)) {
            ossUtil.deleteFile(videoPath);
            if (coverUrl != null && !coverUrl.isEmpty()) ossUtil.deleteFile(coverUrl);
            chunkUtil.cleanTempFiles(uploadId);
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "上传失败");
        }

        chunkUtil.cleanTempFiles(uploadId);
        return ResultData.success("上传完成");
    }

    /**
     * 取消上传
     *
     * @param uploadId 上传ID
     * @return ResultData对象
     */
    @Override
    public ResultData<String> cancelUpload(String uploadId) {
        chunkUtil.cleanTempFiles(uploadId);
        return ResultData.success("操作成功");
    }
}
