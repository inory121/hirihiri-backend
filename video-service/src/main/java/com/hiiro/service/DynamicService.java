package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Dynamic;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.DynamicPublishDTO;

import java.util.Map;

/**
 * <p>
 * 动态表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
public interface DynamicService extends IService<Dynamic> {

    /**
     * 发布动态
     *
     * @param uid 发布者用户ID
     * @param dto 动态内容
     * @return 发布结果
     */
    ResultData<String> publish(Long uid, DynamicPublishDTO dto);

    /**
     * 分页获取动态列表
     *
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @param type       类型 0全部 1视频投稿
     * @param uid        发布者UID过滤（null表示全部）
     * @param currentUid 当前登录用户UID（用于填充isFollowing，null表示未登录）
     * @return 动态列表 {records, total}
     */
    ResultData<Map<String, Object>> getDynamicList(Integer pageNum, Integer pageSize, Integer type, Long uid, Long currentUid);

    /**
     * 分页获取发过动态的UP主列表（按最近发动态时间倒序）
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return {records: [DynamicUpDTO], total}
     */
    ResultData<Map<String, Object>> getUpList(Integer pageNum, Integer pageSize, Long currentUid);

    /**
     * 删除动态（仅动态发布者本人可删除）
     *
     * @param uid 当前登录用户ID
     * @param id  动态ID
     * @return 删除结果
     */
    ResultData<String> delete(Long uid, Long id);

    /**
     * 点赞/取消点赞动态（幂等切换）
     *
     * @param dynamicId 动态ID
     * @param uid       当前登录用户ID
     * @return {liked: boolean, likeCount: int}
     */
    ResultData<Map<String, Object>> toggleLike(Long dynamicId, Long uid);
}
