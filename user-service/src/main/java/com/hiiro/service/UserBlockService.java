package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.UserBlock;
import com.hiiro.entity.dto.UserDTO;

import java.util.List;
import java.util.Map;

/**
 * 用户黑名单服务 —— 真"拉黑"关系管理
 * 与推荐流的"屏蔽作者视频"（recommend:blocked:author）是两套独立机制，互不影响
 */
public interface UserBlockService extends IService<UserBlock> {

    /**
     * 拉黑用户
     *
     * @param blockerUid 执行拉黑的用户 uid
     * @param blockedUid 被拉黑的用户 uid
     * @return 操作结果提示信息
     */
    ResultData<String> blockUser(Long blockerUid, Long blockedUid);

    /**
     * 取消拉黑
     *
     * @param blockerUid 拉黑发起人 uid
     * @param blockedUid 被拉黑的用户 uid
     * @return 操作结果提示信息
     */
    ResultData<String> unblockUser(Long blockerUid, Long blockedUid);

    /**
     * 分页获取当前用户的黑名单列表
     *
     * @param uid      当前用户 uid
     * @param pageNum  页码（可选，默认 1）
     * @param pageSize 每页数量（可选，默认 30）
     * @return 被拉黑用户信息列表
     */
    ResultData<List<UserDTO>> getBlockList(Long uid, Integer pageNum, Integer pageSize);

    /**
     * 判断单向拉黑关系：blockerUid 是否拉黑了 blockedUid
     *
     * @param blockerUid 拉黑发起人 uid
     * @param blockedUid 被拉黑用户 uid
     * @return true = 已拉黑
     */
    boolean isBlocked(Long blockerUid, Long blockedUid);

    /**
     * 判断两个用户之间是否存在任一方向的拉黑关系
     * 用于私信拦截：A 拉黑 B 后，B 发不给 A，A 也发不出（对齐 B 站双向禁止语义）
     *
     * @param uidA 用户A uid
     * @param uidB 用户B uid
     * @return true = 存在拉黑关系
     */
    boolean hasBlockBetween(Long uidA, Long uidB);

    /**
     * 获取当前用户拉黑的全部 uid（不分页，供内部逻辑过滤）
     *
     * @param uid 当前用户 uid
     * @return 被拉黑 uid 列表，无则空列表
     */
    List<Long> getBlockedUids(Long uid);

    /**
     * 查询当前用户与目标用户之间的双向拉黑关系（供 space 页互访拦截）
     *
     * @param uid       当前登录用户 uid
     * @param targetUid 目标用户 uid
     * @return map：blockedByMe=当前用户是否拉黑了目标，blockingMe=目标是否拉黑了当前用户
     */
    Map<String, Boolean> getRelation(Long uid, Long targetUid);
}
