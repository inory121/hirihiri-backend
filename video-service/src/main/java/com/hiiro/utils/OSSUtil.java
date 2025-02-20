package com.hiiro.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.*;
import com.hiiro.config.OSSConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@Component
@RequiredArgsConstructor
public class OSSUtil {
    private final OSS ossClient;
    private final ExecutorService uploadThreadPool;
    private final OSSConfig ossConfig;

    public void uploadFileMultipart(String objectKey, File localFile) throws IOException {
        List<PartETag> partETags = Collections.synchronizedList(new ArrayList<>());
        String uploadId = initiateMultipartUpload(objectKey);

        try {
            long fileLength = localFile.length();
            long partSize = ossConfig.getPartSizeMB() * 1024 * 1024L;
            int partCount = calculatePartCount(fileLength, partSize);

            uploadPartsConcurrently(objectKey, localFile, uploadId, partSize, partCount, partETags);
            completeUpload(objectKey, uploadId, partETags);
        } finally {
            // 添加文件删除逻辑
            if (localFile != null && localFile.exists()) {
                boolean deleted = localFile.delete();
                if (!deleted) {
                    localFile.deleteOnExit(); // 强制 JVM 退出时删除
                }
            }
//            ossClient.shutdown();
        }
    }

    private String initiateMultipartUpload(String objectKey) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                ossConfig.getBucketName(), objectKey);
        return ossClient.initiateMultipartUpload(request).getUploadId();
    }

    private int calculatePartCount(long fileLength, long partSize) {
        int partCount = (int) (fileLength / partSize);
        if (fileLength % partSize != 0) partCount++;
        if (partCount > 10000) {
            throw new IllegalArgumentException("Exceed maximum parts limit (10000)");
        }
        return partCount;
    }

    private void uploadPartsConcurrently(String objectKey, File file, String uploadId,
                                         long partSize, int partCount, List<PartETag> partETags) {
        CountDownLatch latch = new CountDownLatch(partCount);

        for (int i = 0; i < partCount; i++) {
            final int partNumber = i + 1;
            long startPos = i * partSize;
            long curPartSize = (i == partCount - 1) ?
                    (file.length() - startPos) : partSize;

            uploadThreadPool.execute(() -> {
                try {
                    uploadPart(objectKey, file, uploadId, partNumber, startPos, curPartSize, partETags);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void uploadPart(String objectKey, File file, String uploadId,
                            int partNumber, long startPos, long partSize,
                            List<PartETag> partETags) {
        try (InputStream instream = new FileInputStream(file)) {
            instream.skip(startPos);

            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(ossConfig.getBucketName());
            request.setKey(objectKey);
            request.setUploadId(uploadId);
            request.setPartNumber(partNumber);
            request.setPartSize(partSize);
            request.setInputStream(instream);

            UploadPartResult result = ossClient.uploadPart(request);
            synchronized (partETags) {
                partETags.add(result.getPartETag());
            }
        } catch (Exception e) {
            throw new RuntimeException("Part upload failed", e);
        }
    }

    private void completeUpload(String objectKey, String uploadId, List<PartETag> partETags) {
        partETags.sort(Comparator.comparingInt(PartETag::getPartNumber));

        CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(
                ossConfig.getBucketName(), objectKey, uploadId, partETags);

        ossClient.completeMultipartUpload(completeRequest);
    }
}
