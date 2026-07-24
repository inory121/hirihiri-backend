package com.hiiro.service.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiiro.entity.Video;
import com.hiiro.entity.VideoStat;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.mapper.VideoStatMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 热度分计算定时任务
 * 每 5 分钟计算一次所有有效视频的热度分并更新到 video 表
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Slf4j
@Component
public class HotScoreCalculator {

    @Resource
    private VideoMapper videoMapper;
    @Resource
    private VideoStatMapper videoStatMapper;

    /**
     * 每 5 分钟计算一次热度分
     */
    @Scheduled(fixedRate = 300_000)
    public void calculateHotScore() {
        long t0 = System.currentTimeMillis();
        try {
            // 1. 查询所有有效视频
            List<Video> videos = videoMapper.selectList(
                    new LambdaQueryWrapper<Video>()
                            .eq(Video::getStatus, (byte) 1)
                            .select(Video::getVid, Video::getUid, Video::getCreateDate));
            if (videos.isEmpty()) {
                return;
            }

            List<Long> vidList = videos.stream().map(Video::getVid).toList();

            // 2. 批量查询统计数据
            List<VideoStat> stats = videoStatMapper.selectList(
                    new LambdaQueryWrapper<VideoStat>()
                            .in(VideoStat::getVid, vidList));
            Map<Long, VideoStat> statMap = stats.stream()
                    .collect(Collectors.toMap(VideoStat::getVid, s -> s));

            // 3. 计算热度分并批量更新
            LocalDateTime now = LocalDateTime.now();
            List<Video> toUpdate = new ArrayList<>();

            for (Video video : videos) {
                VideoStat stat = statMap.get(video.getVid());
                double hotScore = computeScore(video, stat, now);
                if (Double.isNaN(hotScore) || Double.isInfinite(hotScore)) {
                    hotScore = 0;
                }
                Video update = new Video();
                update.setVid(video.getVid());
                update.setHotScore(hotScore);
                toUpdate.add(update);
            }

            // 4. 批量更新（每批 500 条）
            int batchSize = 500;
            for (int i = 0; i < toUpdate.size(); i += batchSize) {
                List<Video> batch = toUpdate.subList(i, Math.min(i + batchSize, toUpdate.size()));
                for (Video v : batch) {
                    videoMapper.updateById(v);
                }
            }

            log.info("热度分计算完成, 视频数={}, 耗时={}ms", videos.size(), System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("热度分计算失败", e);
        }
    }

    /**
     * 计算单条视频的热度分
     *
     * @param video 视频信息
     * @param stat  统计数据
     * @param now   当前时间
     * @return 热度分
     */
    private double computeScore(Video video, VideoStat stat, LocalDateTime now) {
        int view = stat != null && stat.getView() != null ? stat.getView() : 0;
        int like = stat != null && stat.getLike() != null ? stat.getLike() : 0;
        int favorite = stat != null && stat.getFavorite() != null ? stat.getFavorite() : 0;
        int coin = stat != null && stat.getCoin() != null ? stat.getCoin() : 0;
        int dislike = stat != null && stat.getDislike() != null ? stat.getDislike() : 0;

        // 内容质量（基于互动率）
        double quality = 0;
        if (view > 0) {
            double likeRate = (double) like / view;
            double favRate = (double) favorite / view;
            double coinRate = (double) coin / view;
            double disRate = (double) dislike / view;
            quality = Math.log1p(view) / 10.0
                    + 3.0 * likeRate
                    + 5.0 * favRate
                    + 4.0 * coinRate
                    - 6.0 * disRate;
        } else {
            quality = Math.log1p(view) / 10.0;
        }

        // 新鲜度衰减
        double freshness = 1.0;
        if (video.getCreateDate() != null) {
            long hours = ChronoUnit.HOURS.between(video.getCreateDate(), now);
            freshness = Math.exp(-hours / 168.0); // 168 小时 = 7 天
        }

        return quality * freshness;
    }
}