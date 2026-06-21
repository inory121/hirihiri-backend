package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.VideoStat;
import com.hiiro.mapper.VideoStatMapper;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class VideoStatServiceImpl extends ServiceImpl<VideoStatMapper, VideoStat> implements VideoStatService {

    @Resource
    private VideoStatMapper videoStatMapper;

    @Override
    public VideoStat getVideoStatByVid(Integer vid) {
        VideoStat videoStat = videoStatMapper.selectOne(new LambdaQueryWrapper<VideoStat>().eq(VideoStat::getVid, vid));
        if (Objects.nonNull(videoStat)) {
            return videoStat;
        }
        return new VideoStat();
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
            return 1;
        }
        return updated;
    }

    @Override
    public int incrementReply(Long vid) {
        return incrementBySql(vid, "reply");
    }

    @Override
    public int incrementDanmaku(Long vid) {
        return incrementBySql(vid, "danmaku");
    }

    @Override
    public void incrementPlay(Long vid) {
        incrementBySql(vid, "view");
    }
}
