package com.hiiro.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.UserBlock;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.UserBlockMapper;
import com.hiiro.service.FollowService;
import com.hiiro.service.UserBlockService;
import com.hiiro.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserBlockServiceImpl extends ServiceImpl<UserBlockMapper, UserBlock> implements UserBlockService {

    @Resource
    private UserService userService;

    @Resource
    private FollowService followService;

    /**
     * 拉黑用户（幂等：已拉黑则直接返回成功）
     *
     * @param blockerUid 执行拉黑的用户 uid
     * @param blockedUid 被拉黑的用户 uid
     * @return 操作结果提示信息
     */
    @Transactional
    @Override
    public ResultData<String> blockUser(Long blockerUid, Long blockedUid) {
        if (blockerUid == null || blockedUid == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数无效");
        }
        if (blockerUid.equals(blockedUid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "不能拉黑自己");
        }
        if (userService.getUserByUid(blockedUid) == null) {
            return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
        }

        boolean already = isBlocked(blockerUid, blockedUid);
        if (!already) {
            UserBlock block = new UserBlock();
            block.setBlockerUid(blockerUid);
            block.setBlockedUid(blockedUid);
            try {
                save(block);
            } catch (DuplicateKeyException e) {
                // 并发重复提交，唯一索引兜底，视为已拉黑
                log.debug("重复拉黑已忽略, blockerUid={}, blockedUid={}", blockerUid, blockedUid);
            }
        }
        // 无论是否已拉黑，都取消双向关注（兼容历史已拉黑但未取关的数据）
        followService.removeFollowBetween(blockerUid, blockedUid);
        return ResultData.success(already ? "已在黑名单中" : "已加入黑名单");
    }

    /**
     * 取消拉黑
     *
     * @param blockerUid 拉黑发起人 uid
     * @param blockedUid 被拉黑的用户 uid
     * @return 操作结果提示信息
     */
    @Override
    public ResultData<String> unblockUser(Long blockerUid, Long blockedUid) {
        if (blockerUid == null || blockedUid == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数无效");
        }
        boolean removed = lambdaUpdate()
                .eq(UserBlock::getBlockerUid, blockerUid)
                .eq(UserBlock::getBlockedUid, blockedUid)
                .remove();
        if (!removed) {
            return ResultData.fail(ResultCodeEnum.NOT_FOUND, "该用户不在黑名单中");
        }
        return ResultData.success("已移出黑名单");
    }

    /**
     * 分页获取当前用户的黑名单列表（按拉黑时间倒序）
     *
     * @param uid      当前用户 uid
     * @param pageNum  页码（可选，默认 1）
     * @param pageSize 每页数量（可选，默认 30）
     * @return 被拉黑用户信息列表
     */
    @Override
    public ResultData<List<UserDTO>> getBlockList(Long uid, Integer pageNum, Integer pageSize) {
        pageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        pageSize = (pageSize == null || pageSize < 1) ? 30 : Math.min(pageSize, 100);
        int offset = (pageNum - 1) * pageSize;

        List<Long> blockedUids = lambdaQuery()
                .select(UserBlock::getBlockedUid)
                .eq(UserBlock::getBlockerUid, uid)
                .orderByDesc(UserBlock::getCreateTime)
                .last("LIMIT " + offset + ", " + pageSize)
                .list()
                .stream()
                .map(UserBlock::getBlockedUid)
                .collect(Collectors.toList());

        if (blockedUids.isEmpty()) {
            return ResultData.success(new ArrayList<>());
        }
        return ResultData.success(userService.getBatchUserInfo(blockedUids));
    }

    /**
     * 判断两个用户之间是否存在任一方向的拉黑关系
     *
     * @param uidA 用户A uid
     * @param uidB 用户B uid
     * @return true = 存在拉黑关系
     */
    @Override
    public boolean hasBlockBetween(Long uidA, Long uidB) {
        if (uidA == null || uidB == null) {
            return false;
        }
        return isBlocked(uidA, uidB) || isBlocked(uidB, uidA);
    }

    /**
     * 获取当前用户拉黑的全部 uid
     *
     * @param uid 当前用户 uid
     * @return 被拉黑 uid 列表，无则空列表
     */
    @Override
    public List<Long> getBlockedUids(Long uid) {
        if (uid == null) {
            return List.of();
        }
        return lambdaQuery()
                .select(UserBlock::getBlockedUid)
                .eq(UserBlock::getBlockerUid, uid)
                .list()
                .stream()
                .map(UserBlock::getBlockedUid)
                .collect(Collectors.toList());
    }

    /**
     * 判断单向拉黑关系：blockerUid 是否拉黑了 blockedUid
     *
     * @param blockerUid 拉黑发起人 uid
     * @param blockedUid 被拉黑用户 uid
     * @return true = 已拉黑
     */
    @Override
    public boolean isBlocked(Long blockerUid, Long blockedUid) {
        if (blockerUid == null || blockedUid == null) {
            return false;
        }
        return lambdaQuery()
                .eq(UserBlock::getBlockerUid, blockerUid)
                .eq(UserBlock::getBlockedUid, blockedUid)
                .count() > 0;
    }

    /**
     * 查询当前用户与目标用户之间的双向拉黑关系
     *
     * @param uid       当前登录用户 uid
     * @param targetUid 目标用户 uid
     * @return map：blockedByMe / blockingMe
     */
    @Override
    public Map<String, Boolean> getRelation(Long uid, Long targetUid) {
        Map<String, Boolean> result = new HashMap<>(2);
        boolean blockedByMe = isBlocked(uid, targetUid);
        boolean blockingMe = isBlocked(targetUid, uid);
        result.put("blockedByMe", blockedByMe);
        result.put("blockingMe", blockingMe);
        return result;
    }
}
