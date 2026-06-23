package com.hiiro.service;

import com.hiiro.entity.ResultData;

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
     * 投币/取消投币
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @return 操作结果
     */
    ResultData<String> toggleCoin(Long uid, Long vid);

    /**
     * 收藏/取消收藏
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @return 操作结果
     */
    ResultData<String> toggleCollect(Long uid, Long vid);

    /**
     * 获取用户对视频的互动状态
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @return 互动状态（liked、coined、favorited）
     */
    ResultData<Boolean[]> getInteractionStatus(Long uid, Long vid);
}