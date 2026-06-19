package com.hiiro.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 在线观众统计服务
 * 使用 Redis ZSET 实现，Score 为最后心跳时间戳
 */
@Slf4j
@Service
public class OnlineViewerService {

    private static final String KEY_PREFIX = "online:";
    private static final long HEARTBEAT_TIMEOUT = 60_000L; // 60秒无心跳视为离线

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 发送心跳，更新观众在线状态
     *
     * @param vid      视频ID
     * @param viewerId 观众标识 (uid 或 cookie UUID)
     * @return 当前在线人数
     */
    public long heartbeat(Long vid, String viewerId) {
        String key = KEY_PREFIX + vid;
        long now = System.currentTimeMillis();

        // ZADD: 添加/更新观众的心跳时间
        redisTemplate.opsForZSet().add(key, viewerId, now);
        // 设置 key 2分钟过期，无人观看时自动清理
        redisTemplate.expire(key, 120, java.util.concurrent.TimeUnit.SECONDS);

        // ZCOUNT: 统计60秒内活跃的观众数
        long minScore = now - HEARTBEAT_TIMEOUT;
        Long count = redisTemplate.opsForZSet().count(key, minScore, Double.MAX_VALUE);
        return count != null ? count : 0;
    }

    /**
     * 定时清理过期心跳数据 (每5分钟执行一次)
     * 清除所有视频中超过60秒未更新心跳的记录
     */
    @Scheduled(fixedRate = 300_000) // 5分钟
    public void cleanupExpiredViewers() {
        long now = System.currentTimeMillis();
        long minScore = now - HEARTBEAT_TIMEOUT;

        // 扫描所有 online:* 的 key
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        int cleanedKeys = 0;
        long totalRemoved = 0;

        for (String key : keys) {
            // 删除60秒前的心跳记录
            Long removed = redisTemplate.opsForZSet().removeRangeByScore(key, 0, minScore);
            if (removed != null && removed > 0) {
                totalRemoved += removed;
            }

            // 如果集合为空，删除整个 key
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size == null || size == 0) {
                redisTemplate.delete(key);
                cleanedKeys++;
            }
        }

        if (totalRemoved > 0 || cleanedKeys > 0) {
            log.info("[OnlineViewer] 定时清理完成: 移除 {} 条过期心跳, 清理 {} 个空key", totalRemoved, cleanedKeys);
        }
    }
}
