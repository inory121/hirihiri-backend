package com.hiiro.service.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiiro.entity.Video;
import com.hiiro.entity.VideoStat;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.mapper.VideoStatMapper;
import com.hiiro.service.VideoService;
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
 *
 * <p>优化点：
 * <ol>
 *   <li>热度分量化到固定小数位（HOT_SCALE），消除浮点噪声，避免无谓写库；</li>
 *   <li>仅当量化后值与旧值不同才入队更新；</li>
 *   <li>使用 MyBatis-Plus 自带的 updateBatchById 真正批量写入（需配合 rewriteBatchedStatements=true）；</li>
 *   <li>5 分钟任务只处理近期有互动 / 新建的视频（增量）；</li>
 *   <li>每日 3 点全量兜底，保证长期无互动的老视频也能持续衰减。</li>
 * </ol>
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Slf4j
@Component
public class HotScoreCalculator {

    /** 热度分量化倍数：1000=3 位小数（想用 2 位改 100，4 位改 10000） */
    private static final double HOT_SCALE = 1000.0;
    /** 近期有互动的窗口（分钟） */
    private static final int WINDOW_MINUTES = 15;
    /** 仍在衰减窗口内的新建视频（天） */
    private static final int RECENT_DAYS = 30;

    @Resource
    private VideoMapper videoMapper;
    @Resource
    private VideoStatMapper videoStatMapper;
    @Resource
    private VideoService videoService;

    /**
     * 每 5 分钟：只处理近期有互动 / 新建的视频（增量）
     */
    @Scheduled(fixedRate = 300_000)
    public void calculateHotScore() {
        long t0 = System.currentTimeMillis();
        try {
            List<Video> candidates =
                    videoMapper.selectHotScoreCandidates(WINDOW_MINUTES, RECENT_DAYS);
            int n = recompute(candidates);
            log.info("热度分(增量)完成, 候选={}, 实际更新={}, 耗时={}ms",
                    candidates.size(), n, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("热度分计算失败", e);
        }
    }

    /**
     * 每日 3 点全量兜底：保证长期无互动的老视频也能继续衰减
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void calculateHotScoreFull() {
        long t0 = System.currentTimeMillis();
        try {
            List<Video> all = videoMapper.selectList(
                    new LambdaQueryWrapper<Video>()
                            .eq(Video::getStatus, (byte) 1)
                            .select(Video::getVid, Video::getUid,
                                    Video::getCreateTime, Video::getHotScore));
            int n = recompute(all);
            log.info("热度分(全量兜底)完成, 候选={}, 实际更新={}, 耗时={}ms",
                    all.size(), n, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("热度分全量计算失败", e);
        }
    }

    /**
     * 计算 + 量化 + 过滤变化 + 批量写
     */
    private int recompute(List<Video> videos) {
        if (videos.isEmpty()) {
            return 0;
        }

        List<Long> vidList = videos.stream().map(Video::getVid).toList();
        List<VideoStat> stats = videoStatMapper.selectList(
                new LambdaQueryWrapper<VideoStat>().in(VideoStat::getVid, vidList));
        Map<Long, VideoStat> statMap = stats.stream()
                .collect(Collectors.toMap(VideoStat::getVid, s -> s, (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<Video> changed = new ArrayList<>();
        for (Video v : videos) {
            double score = computeScore(v, statMap.get(v.getVid()), now);
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                score = 0;
            }

            double newScore = Math.round(score * HOT_SCALE) / HOT_SCALE;   // 量化
            Double old = v.getHotScore();
            double oldScore = (old == null) ? 0.0 : Math.round(old * HOT_SCALE) / HOT_SCALE;
            if (newScore != oldScore) {          // 量化后确实变了才写
                Video u = new Video();
                u.setVid(v.getVid());
                u.setHotScore(newScore);
                changed.add(u);
            }
        }

        if (!changed.isEmpty()) {
            videoService.updateBatchById(changed);   // MP 自带，内部开 BATCH 会话
        }
        return changed.size();
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
        double quality;
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
        if (video.getCreateTime() != null) {
            long hours = ChronoUnit.HOURS.between(video.getCreateTime(), now);
            freshness = Math.exp(-hours / 168.0); // 168 小时 = 7 天
        }

        return quality * freshness;
    }
}
