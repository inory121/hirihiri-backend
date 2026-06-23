package com.hiiro.service.impl;

import com.hiiro.utils.ChunkUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 临时文件定时清理服务
 * 清理用户上传中断后遗留的临时分片文件
 */
@Slf4j
@Service
public class TempFileCleanupService {

    @Resource
    private ChunkUtil chunkUtil;

    @Value("${video.upload.tmp:tmp/uploads}")
    private String tempDir;

    @Value("${video.upload.cleanup.enable:true}")
    private boolean cleanupEnabled;

    @Value("${video.upload.cleanup.expire-hours:24}")
    private int expireHours;

    @Value("${video.upload.cleanup.interval-minutes:60}")
    private int cleanupIntervalMinutes;

    /**
     * 定时清理过期临时文件
     * 默认每小时执行一次，清理超过24小时未更新的上传目录
     */
    @Scheduled(fixedRateString = "${video.upload.cleanup.interval-millis:3600000}", initialDelay = 300000)
    public void cleanupExpiredTempFiles() {
        if (!cleanupEnabled) {
            return;
        }

        log.info("========== 开始定时清理临时文件 ==========");
        Path uploadRoot = Paths.get(tempDir);

        if (!Files.exists(uploadRoot)) {
            log.info("临时目录不存在: {}", uploadRoot);
            return;
        }

        long expireTime = System.currentTimeMillis() - (expireHours * 60L * 60L * 1000L);
        AtomicInteger cleanedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        try (var paths = Files.list(uploadRoot)) {
            paths.filter(Files::isDirectory)
                    .forEach(uploadDir -> {
                        try {
                            // 获取目录最后修改时间
                            long lastModified = Files.getLastModifiedTime(uploadDir).toMillis();
                            
                            // 判断是否过期
                            if (lastModified < expireTime) {
                                String uploadId = uploadDir.getFileName().toString();
                                log.info("清理过期临时文件: uploadId={}, 最后修改时间={}", 
                                        uploadId, 
                                        LocalDateTime.ofInstant(
                                            java.time.Instant.ofEpochMilli(lastModified), 
                                            ZoneId.systemDefault()));
                                
                                chunkUtil.cleanTempFiles(uploadId);
                                cleanedCount.incrementAndGet();
                            }
                        } catch (IOException e) {
                            log.error("清理临时文件失败: {}", uploadDir, e);
                            failedCount.incrementAndGet();
                        }
                    });
        } catch (IOException e) {
            log.error("遍历临时目录失败: {}", uploadRoot, e);
        }

        log.info("========== 定时清理完成 ==========");
        log.info("已清理: {} 个, 失败: {} 个", cleanedCount.get(), failedCount.get());
    }

    /**
     * 手动触发清理（可通过API调用）
     *
     * @return 清理结果信息
     */
    public String triggerCleanup() {
        log.info("手动触发临时文件清理");
        cleanupExpiredTempFiles();
        return "清理任务已触发";
    }

    /**
     * 获取清理配置信息
     *
     * @return 配置信息
     */
    public Map<String, Object> getCleanupConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", cleanupEnabled);
        config.put("expireHours", expireHours);
        config.put("intervalMinutes", cleanupIntervalMinutes);
        config.put("tempDir", tempDir);
        return config;
    }
}
