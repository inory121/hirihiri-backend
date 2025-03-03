package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.VideoService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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
     *
     * @return ResultData对象
     */
    @Override
    public ResultData<List<Video>> getRecommendVideos() {
        List<Video> videoList = new LambdaQueryChainWrapper<>(Video.class).ne(Video::getStatus, "3").list();
        return ResultData.success(videoList);
    }

    /**
     * 保存视频
     *
     * @param uid   用户id
     * @param video 视频对象
     */
    @Transactional
    @Override
    public void saveVideo(String uid, Video video) {
        video.setUid(Integer.valueOf(uid));
        if (videoMapper.insert(video) == 1) {
            ResultData.success("保存视频成功");
        } else {
            ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "保存视频失败");
        }
    }

    /**
     * 根据视频id获取视频
     *
     * @param vid 视频id
     * @return ResultData对象
     */
    @Override
    public ResultData<Video> getVideoById(Integer vid) {
        Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>().eq(Video::getVid, vid));
        if (Objects.isNull(video)){
            return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST);
        }
        return ResultData.success(video, "获取视频信息成功");
    }
}
