package com.hiiro.service;

import com.hiiro.entity.RecommendEvent;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.RecommendFeedDTO;

import java.util.List;
import java.util.Map;

/**
 * 推荐服务接口
 *
 * @author hiiro
 * @since 2025-07-23
 */
public interface RecommendService {

    /**
     * 获取推荐流
     *
     * @param cursor 游标，首次为空
     * @param scene  场景：home/related/search
     * @param limit  每页数量，默认20
     * @param uid    当前用户ID（未登录为null）
     * @return 推荐流响应
     */
    ResultData<RecommendFeedDTO> getFeed(String cursor, String scene, Integer limit, Long uid);

    /**
     * 批量上报推荐事件
     *
     * @param events 事件列表
     * @param uid    当前登录用户ID（从认证上下文获取，忽略请求体中的 uid）
     * @return 上报结果
     */
    ResultData<String> reportEvents(List<RecommendEvent> events, Long uid);

    /**
     * 撤销点踩事件（取消点踩时删除已记录的 dislike 事件）
     *
     * @param uid       用户ID
     * @param vid       视频ID
     * @param requestId 推荐请求ID
     * @param scene     场景
     * @return 操作结果
     */
    ResultData<String> revokeDislike(Long uid, Long vid, String requestId, String scene);

    /**
     * 获取相关推荐视频
     *
     * @param vid   当前视频ID
     * @param limit 返回数量
     * @return 相关视频列表
     */
    ResultData<List<Map<String, Object>>> getRelatedVideos(Long vid, Integer limit);

    /**
     * 标记不感兴趣
     *
     * @param uid 当前用户ID
     * @param vid 视频ID
     * @return 操作结果
     */
    ResultData<String> notInterested(Long uid, Long vid);

    /**
     * 屏蔽作者
     *
     * @param uid      当前用户ID
     * @param authorUid 作者ID
     * @return 操作结果
     */
    ResultData<String> blockAuthor(Long uid, Long authorUid);
}