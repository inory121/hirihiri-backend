package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;

import java.util.List;

/**
 * <p>
 * 视频表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
public interface VideoService extends IService<Video> {

    /**
     * 获取推荐视频
     * @return ResultData对象
     */
    ResultData<List<Video>> getRecommendVideos();

    /**
     * 保存视频
     *
     * @param uid   用户id
     * @param video 视频对象
     */
    void saveVideo(String uid, Video video);

    /**
     * 根据视频id获取视频
     *
     * @param vid 视频id
     * @return ResultData对象
     */
    ResultData<Video> getVideoById(Integer vid);
}
