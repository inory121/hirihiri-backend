package com.hiiro.service;

import com.hiiro.entity.ResultData;

import java.util.List;
import java.util.Map;

/**
 * 视频互动服务（点赞、投币、收藏）
 *
 * @author hiiro
 * @since 2025-06-23
 */
public interface VideoInteractionService {

    /**
     * 点赞/取消点赞
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @return 操作结果
     */
    ResultData<String> toggleLike(Long uid, Long vid);

    /**
     * 点踩/取消点踩
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @return 操作结果
     */
    ResultData<String> toggleDislike(Long uid, Long vid);

    /**
     * 投币/取消投币
     *
     * @param uid   用户ID
     * @param vid   视频ID
     * @param count 投币数量（1或2）
     * @return 操作结果
     */
    ResultData<String> toggleCoin(Long uid, Long vid, Integer count);

    /**
     * 收藏到指定收藏夹
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @param folderId 收藏夹ID
     * @return 操作结果
     */
    ResultData<String> collectToFolder(Long uid, Long vid, Long folderId);

    /**
     * 获取用户对视频的互动状态
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @return 互动状态 [liked, disliked, coined, favorited]
     */
    ResultData<Object[]> getInteractionStatus(Long uid, Long vid);

    /**
     * 获取用户最近投币的视频列表
     *
     * @param uid 用户ID
     * @param limit 限制数量
     * @return 视频列表
     */
    ResultData<List<Map<String, Object>>> getRecentCoinVideos(Long uid, Integer limit);

    /**
     * 获取用户最近点赞的视频列表
     *
     * @param uid 用户ID
     * @param limit 限制数量
     * @return 视频列表
     */
    ResultData<List<Map<String, Object>>> getRecentLikeVideos(Long uid, Integer limit);
}