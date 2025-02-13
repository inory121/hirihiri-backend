package com.hiiro.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.VideoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 视频表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Resource
    VideoMapper videoMapper;

    /**
     * 首页获取推荐视频
     * @return ResultData对象
     */
    @Override
    public ResultData<List<Video>> getRecommendVideos() {
        List<Video> videoList = new LambdaQueryChainWrapper<>(Video.class).ne(Video::getVid, "3").list();
        return ResultData.success(videoList);
    }
}
