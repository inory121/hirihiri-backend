package com.hiiro.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import com.hiiro.config.OSSConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OSS文件工具类，提供分片上传相关操作
 */
@Slf4j
@Component
public class OSSUtil {
    @Resource
    private OSS ossClient;
    @Resource
    private ExecutorService uploadThreadPool;
    @Resource
    private OSSConfig ossConfig;

    /**
     * 上传文件
     *
     * @param objectKey 文件名
     * @param file      文件
     * @return 文件访问地址
     */
    public String uploadFile(String objectKey, MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(getContentType(objectKey));
            metadata.setContentLength(file.getSize());

            PutObjectRequest request = new PutObjectRequest(
                    ossConfig.getBucketName(),
                    objectKey,
                    is,
                    metadata
            );

            ossClient.putObject(request);
            return ossConfig.getBucketUrl() + "/" + objectKey;
        } catch (Exception e) {
            throw new RuntimeException("封面文件上传失败", e);
        }
    }

    /**
     * 直接上传已分片的文件（前端已预先分片）
     *
     * @param objectKey 文件名
     * @param chunks    分片文件列表（需按顺序排列）
     * @apiNote 特性：
     * 1. 使用线程池并发上传分片
     * 2. 自动合并分片并完成上传
     * 3. 上传完成后自动清理分片文件
     */
    public String uploadPartsDirectly(String objectKey, List<File> chunks) {
        List<PartETag> partETags = Collections.synchronizedList(new ArrayList<>());
        String uploadId = initiateMultipartUpload(objectKey);

        try {
            CountDownLatch latch = new CountDownLatch(chunks.size());
            AtomicReference<Exception> firstError = new AtomicReference<>();

            for (int i = 0; i < chunks.size(); i++) {
                final int partNumber = i + 1;
                final File chunk = chunks.get(i);

                uploadThreadPool.execute(() -> {
                    try (InputStream is = new FileInputStream(chunk)) {
                        // 复用原有上传逻辑
                        uploadPart(objectKey, chunk.getName(), uploadId, partNumber, is, chunk.length(), partETags);
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            if (!latch.await(10, TimeUnit.MINUTES)) {
                throw new RuntimeException("分片上传超时");
            }
            if (firstError.get() != null) {
                throw new RuntimeException("分片上传失败", firstError.get());
            }
            completeUpload(objectKey, uploadId, partETags);
            return ossConfig.getBucketUrl() + "/" + objectKey;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("分片上传被中断", e);
        } finally {
//            chunks.forEach(File::delete); // 清理分片文件
        }
    }

    /**
     * 执行单个分片上传
     *
     * @param chunkName   分片文件名（用于日志记录）
     * @param uploadId    上传任务ID
     * @param partNumber  分片序号（从1开始）
     * @param inputStream 分片数据流（方法内会自动关闭）
     */
    private void uploadPart(String objectKey, String chunkName, String uploadId,
                            int partNumber, InputStream inputStream, long partSize,
                            List<PartETag> partETags) {
        try {
            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(ossConfig.getBucketName());
            request.setKey(objectKey);
            request.setUploadId(uploadId);
            request.setPartNumber(partNumber);
            request.setPartSize(partSize);
            request.setInputStream(inputStream);

            UploadPartResult result = ossClient.uploadPart(request);
            synchronized (partETags) {
                partETags.add(result.getPartETag());
            }
            log.info("分片 {} 上传成功 (大小: {} MB)", chunkName, partSize / 1024 / 1024);
        } catch (Exception e) {
            throw new RuntimeException("分片上传失败: " + chunkName, e);
        }
    }

    /**
     * 初始化分片上传任务
     *
     * @param objectKey 文件名
     * @return 返回本次上传任务的唯一标识 uploadId
     */
    private String initiateMultipartUpload(String objectKey) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                ossConfig.getBucketName(), objectKey);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(getContentType(objectKey));
        request.setObjectMetadata(metadata);
        return ossClient.initiateMultipartUpload(request).getUploadId();
    }

    /**
     * 获取文件类型对应的ContentType
     *
     * @param fileName 文件名
     * @return ContentType
     */
    private String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "mp4" -> "video/mp4";
            case "flv" -> "video/x-flv";
            case "avi" -> "video/x-msvideo";
            case "wmv" -> "video/x-ms-wmv";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    /**
     * 完成分片上传任务
     *
     * @param objectKey 文件名
     * @param uploadId  上传任务ID
     * @param partETags 分片上传结果列表
     */
    private void completeUpload(String objectKey, String uploadId, List<PartETag> partETags) {
        partETags.sort(Comparator.comparingInt(PartETag::getPartNumber));

        CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(
                ossConfig.getBucketName(), objectKey, uploadId, partETags);

        ossClient.completeMultipartUpload(completeRequest);
    }

    /**
     * 删除文件
     *
     * @param videoPath 文件路径
     */
    public void deleteFile(String videoPath) {
        try {
            ossClient.deleteObject(ossConfig.getBucketName(), videoPath);
        } catch (Exception e) {
            log.error("文件删除失败: {}", videoPath, e);
        }
    }
}
