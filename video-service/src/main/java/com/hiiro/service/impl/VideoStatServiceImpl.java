package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.VideoStat;
import com.hiiro.mapper.VideoStatMapper;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-03-09
 */
@Service
public class VideoStatServiceImpl extends ServiceImpl<VideoStatMapper, VideoStat> implements VideoStatService {

    @Resource
    private VideoStatMapper videoStatMapper;

    /**
     * 根据视频ID查询视频统计数据
     *
     * @param vid 视频ID
     * @return 视频统计数据
     */
    @Override
    public VideoStat getVideoStatByVid(Integer vid) {
        VideoStat videoStat = videoStatMapper.selectOne(new LambdaQueryWrapper<VideoStat>().eq(VideoStat::getVid, vid));
        if (Objects.nonNull(videoStat)) {
            return videoStat;
        }
        return new VideoStat();
    }

    /**
     * 保存视频统计数据
     *
     * @param vid 视频ID
     * @return 插入记录数
     */
    @Transactional
    @Override
    public int saveVideoStat(Long vid) {
        VideoStat videoStat = new VideoStat();
        videoStat.setVid(vid);
        return videoStatMapper.insert(videoStat);
    }

    /**
     * 视频的回复数+1
     *
     * @param vid 视频ID
     * @return 更新记录数
     */
    @Transactional
    @Override
    public int incrementReply(Long vid) {
        return videoStatMapper.update(new LambdaUpdateWrapper<VideoStat>()
                .eq(VideoStat::getVid, vid)
                .setSql("reply = reply + 1"));
    }

    /**
     * 视频的弹幕数+1
     *
     * @param vid 视频ID
     * @return 更新记录数
     */
    @Transactional
    @Override
    public int incrementDanmaku(Long vid) {
        return videoStatMapper.update(new LambdaUpdateWrapper<VideoStat>()
                .eq(VideoStat::getVid, vid)
                .setSql("danmaku = danmaku + 1"));
    }
}
