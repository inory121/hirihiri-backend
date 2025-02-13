package com.hiiro.service;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.baomidou.mybatisplus.extension.service.IService;

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
}
