package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    ResultData<List<Map<String,Object>>> getRecommendVideos(Integer pageNum, Integer pageSize);

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
    ResultData<HashMap<String, Object>> getVideoById(Integer vid);

}
