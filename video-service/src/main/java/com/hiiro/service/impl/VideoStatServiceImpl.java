package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Video;
import com.hiiro.entity.VideoStat;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.mapper.VideoStatMapper;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VideoStatServiceImpl extends ServiceImpl<VideoStatMapper, VideoStat> implements VideoStatService {

    private static final String REDIS_KEY_USER_STATS = "user:video:stats:";
    private static final long REDIS_EXPIRE_MINUTES = 5;

    @Resource
    private VideoStatMapper videoStatMapper;

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public VideoStat getVideoStatByVid(Long vid) {
        VideoStat videoStat = videoStatMapper.selectOne(new LambdaQueryWrapper<VideoStat>().eq(VideoStat::getVid, vid));
        if (Objects.nonNull(videoStat)) {
            return videoStat;
        }
        return new VideoStat();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserVideoStats(Long uid) {
        String key = REDIS_KEY_USER_STATS + uid;
        try {
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("获取用户视频统计缓存失败, uid={}", uid, e);
        }

        Map<String, Object> stats = videoStatMapper.selectUserVideoStats(uid);
        if (stats != null) {
            try {
                redisTemplate.opsForValue().set(key, stats, REDIS_EXPIRE_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("设置用户视频统计缓存失败, uid={}", uid, e);
            }
        }
        return stats;
    }

    @Override
    public int saveVideoStat(Long vid) {
        VideoStat videoStat = new VideoStat();
        videoStat.setVid(vid);
        try {
            return videoStatMapper.insert(videoStat);
        } catch (DuplicateKeyException e) {
            return 0;
        }
    }

    private void clearUserStatsCache(Long vid) {
        try {
            Video video = videoMapper.selectById(vid);
            if (video != null && video.getUid() != null) {
                redisTemplate.delete(REDIS_KEY_USER_STATS + video.getUid());
            }
        } catch (Exception e) {
            log.warn("清除用户视频统计缓存失败, vid={}", vid, e);
        }
    }

    private int incrementBySql(Long vid, String column) {
        int updated = videoStatMapper.update(new LambdaUpdateWrapper<VideoStat>()
                .eq(VideoStat::getVid, vid)
                .setSql(column + " = " + column + " + 1"));
        if (updated == 0) {
            try {
                saveVideoStat(vid);
            } catch (Exception ignored) {
            }
            videoStatMapper.update(new LambdaUpdateWrapper<VideoStat>()
                    .eq(VideoStat::getVid, vid)
                    .setSql(column + " = " + column + " + 1"));
        }
        clearUserStatsCache(vid);
        return updated > 0 ? updated : 1;
    }

    @Override
    public int incrementReply(Long vid) {
        return incrementBySql(vid, "reply");
    }

    @Override
    public void decrementReply(Long vid) {
        decrementBySql(vid, "reply");
    }

    @Override
    public void decrementReply(Long vid, int count) {
        if (count <= 0) return;
        int updated = videoStatMapper.update(new LambdaUpdateWrapper<VideoStat>()
                .eq(VideoStat::getVid, vid)
                .setSql("reply = GREATEST(0, reply - " + count + ")"));
        clearUserStatsCache(vid);
    }

    @Override
    public int incrementDanmaku(Long vid) {
        return incrementBySql(vid, "danmaku");
    }

    @Override
    public void incrementPlay(Long vid) {
        incrementBySql(vid, "view");
    }

    @Override
    public void incrementLike(Long vid) {
        incrementBySql(vid, "`like`");
    }

    @Override
    public void decrementLike(Long vid) {
        decrementBySql(vid, "`like`");
    }

    @Override
    public void incrementCoin(Long vid) {
        incrementBySql(vid, "coin");
    }

    @Override
    public void decrementCoin(Long vid) {
        decrementBySql(vid, "coin");
    }

    @Override
    public void incrementFavorite(Long vid) {
        incrementBySql(vid, "favorite");
    }

    @Override
    public void decrementFavorite(Long vid) {
        decrementBySql(vid, "favorite");
    }

    @Override
    public void incrementDislike(Long vid) {
        incrementBySql(vid, "dislike");
    }

    @Override
    public void decrementDislike(Long vid) {
        decrementBySql(vid, "dislike");
    }

    private int decrementBySql(Long vid, String column) {
        int updated = videoStatMapper.update(new LambdaUpdateWrapper<VideoStat>()
                .eq(VideoStat::getVid, vid)
                .setSql(column + " = " + column + " - 1"));
        clearUserStatsCache(vid);
        return updated;
    }
}
