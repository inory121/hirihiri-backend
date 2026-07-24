package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.VideoStat;

import java.util.Map;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author hiiro
 * @since 2025-03-09
 */
public interface VideoStatService extends IService<VideoStat> {

    /**
     * 根据视频ID获取视频统计数据
     *
     * @param vid 视频ID
     * @return 视频统计数据
     */
    VideoStat getVideoStatByVid(Long vid);

    /**
     * 获取用户的视频统计数据汇总（带缓存）
     *
     * @param uid 用户ID
     * @return 统计数据 {totalVideos, totalViews, totalLikes, totalCoins, totalFavorites, totalDanmaku}
     */
    Map<String, Object> getUserVideoStats(Long uid);

    /**
     * 保存视频统计数据
     *
     * @param vid 视频ID
     * @return 保存结果
     */
    int saveVideoStat(Long vid);
    /**
     * 视频的回复数+1
     *
     * @param vid 视频ID
     * @return 增加结果
     */
    int incrementReply(Long vid);
    /**
     * 视频的弹幕数+1
     *
     * @param vid 视频ID
     * @return 增加结果
     */
    int incrementDanmaku(Long vid);
    
    /**
     * 视频的播放量+1 (支持自动创建记录)
     *
     * @param vid 视频ID
     */
    void incrementPlay(Long vid);

    /**
     * 视频的点赞数+1
     *
     * @param vid 视频ID
     */
    void incrementLike(Long vid);

    /**
     * 视频的点赞数-1
     *
     * @param vid 视频ID
     */
    void decrementLike(Long vid);

    /**
     * 视频的投币数+1
     *
     * @param vid 视频ID
     */
    void incrementCoin(Long vid);

    /**
     * 视频的投币数-1
     *
     * @param vid 视频ID
     */
    void decrementCoin(Long vid);

    /**
     * 视频的收藏数+1
     *
     * @param vid 视频ID
     */
    void incrementFavorite(Long vid);

    /**
     * 视频的收藏数-1
     *
     * @param vid 视频ID
     */
    void decrementFavorite(Long vid);

    /**
     * 视频的点踩数+1
     *
     * @param vid 视频ID
     */
    void incrementDislike(Long vid);

    /**
     * 视频的点踩数-1
     *
     * @param vid 视频ID
     */
    void decrementDislike(Long vid);
}
