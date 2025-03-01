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

/**
 * OSS文件工具类，提供分片上传相关操作
 */
@Slf4j
@Component
//@RequiredArgsConstructor
public class OSSUtil {
    @Resource
    private OSS ossClient;
    @Resource
    private ExecutorService uploadThreadPool;
    @Resource
    private OSSConfig ossConfig;
//    @Resource
//    private OSSResumeConfig resumeConfig;

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
            return ossConfig.getBucketUrl() + "/"  + objectKey;
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

            for (int i = 0; i < chunks.size(); i++) {
                final int partNumber = i + 1;
                final File chunk = chunks.get(i);

                uploadThreadPool.execute(() -> {
                    try (InputStream is = new FileInputStream(chunk)) {
                        // 复用原有上传逻辑
                        uploadPart(objectKey, chunk.getName(), uploadId, partNumber, is, chunk.length(), partETags);
                    } catch (Exception e) {
                        throw new RuntimeException("分片上传失败: " + chunk.getName(), e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            completeUpload(objectKey, uploadId, partETags);
            return ossConfig.getBucketUrl() + "/"  + objectKey;
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

//    /**
//     * 断点续传上传文件
//     *
//     * @param objectKey     文件名
//     * @param localFilePath 本地文件路径
//     */
//    public void resumeUpload(String objectKey, String localFilePath) {
//        UploadFileRequest request = new UploadFileRequest(
//                ossConfig.getBucketName(),
//                objectKey,
//                localFilePath,
//                resumeConfig.getPartSizeMB() * 1024 * 1024L,
//                resumeConfig.getTaskNum(),
//                resumeConfig.isEnableCheckpoint()
//        );
//        // 新增ContentType设置
//        ObjectMetadata metadata = new ObjectMetadata();
//        metadata.setContentType(getContentType(objectKey));
//        request.setObjectMetadata(metadata);
//
//        request.setCheckpointFile(getCheckpointPath(objectKey));
//
//        try {
//            UploadFileResult result = ossClient.uploadFile(request);
//            log.info("断点续传成功，ETag: {}", result.getMultipartUploadResult().getETag());
//        } catch (Throwable e) {
//            throw new RuntimeException("断点续传失败", e);
//        }
//    }

//    /**
//     * 获取断点续传的断点信息文件路径
//     *
//     * @param objectKey 文件名
//     * @return 断点续传的断点信息文件路径
//     */
//    private String getCheckpointPath(String objectKey) {
//        return Paths.get(resumeConfig.getCheckpointDir(),
//                        objectKey.replace("/", "_") + ".ucp")
//                .toString();
//    }

//    /**
//     * 多分片上传文件（传统方式，前端上传完整文件后服务端分片）
//     *
//     * @param objectKey 文件名
//     * @param localFile 待上传的本地文件
//     * @throws IOException 文件操作异常
//     * @apiNote 该方法会在上传完成后自动删除本地文件
//     */
//    // 这是原有方法，是在前端传来完整文件再分片，现在改为前端传分片后的文件，也就是上面的uploadPartsDirectly方法
//    public void uploadFileMultipart(String objectKey, File localFile) throws IOException {
//        List<PartETag> partETags = Collections.synchronizedList(new ArrayList<>());
//        String uploadId = initiateMultipartUpload(objectKey);
//
//        try {
//            long fileLength = localFile.length();
//            long partSize = ossConfig.getPartSizeMB() * 1024 * 1024L;
//            int partCount = calculatePartCount(fileLength, partSize);
//
////            uploadPartsConcurrently(objectKey, localFile, uploadId, partSize, partCount, partETags);
//            completeUpload(objectKey, uploadId, partETags);
//        } finally {
//            // 添加文件删除逻辑
//            if (localFile != null && localFile.exists()) {
//                boolean deleted = localFile.delete();
//                if (!deleted) {
//                    localFile.deleteOnExit(); // 强制 JVM 退出时删除
//                }
//            }
//            // ossClient由spring管理，手动关闭会导致用户断开连接
////            ossClient.shutdown();
//        }
//    }

    /**
     * 初始化分片上传任务
     *
     * @param objectKey 文件名
     * @return 返回本次上传任务的唯一标识 uploadId
     */
    private String initiateMultipartUpload(String objectKey) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                ossConfig.getBucketName(), objectKey);
        // 新增ContentType设置
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

//    /**
//     * 计算所需分片数量
//     *
//     * @param fileLength 文件总大小（字节）
//     * @param partSize   单个分片大小（字节）
//     * @return 分片总数
//     * @throws IllegalArgumentException 当分片数超过10000时抛出
//     */
//    private int calculatePartCount(long fileLength, long partSize) {
//        int partCount = (int) (fileLength / partSize);
//        if (fileLength % partSize != 0) partCount++;
//        if (partCount > 10000) {
//            throw new IllegalArgumentException("Exceed maximum parts limit (10000)");
//        }
//        return partCount;
//    }

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

}
