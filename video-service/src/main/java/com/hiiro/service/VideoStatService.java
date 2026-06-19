package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.VideoStat;

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
    VideoStat getVideoStatByVid(Integer vid);

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
}
