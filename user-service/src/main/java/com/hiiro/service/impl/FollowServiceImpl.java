package com.hiiro.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Follow;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.FollowMapper;
import com.hiiro.service.FollowService;
import com.hiiro.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Resource
    private UserService userService;

    /**
     * 关注 / 取消关注
     * 已存在关注记录则删除（取消关注），否则新增一条关注记录
     *
     * @param followerUid  关注者 uid
     * @param followingUid 被关注者 uid
     * @return 操作结果提示信息
     */
    @Override
    public ResultData<String> toggleFollow(Long followerUid, Long followingUid) {
        if (followerUid.equals(followingUid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "不能关注自己");
        }
        if (userService.getUserByUid(followingUid) == null) {
            return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
        }

        Follow existing = lambdaQuery()
                .eq(Follow::getFollowerUid, followerUid)
                .eq(Follow::getFollowingUid, followingUid)
                .one();

        if (existing != null) {
            lambdaUpdate()
                    .eq(Follow::getFollowerUid, followerUid)
                    .eq(Follow::getFollowingUid, followingUid)
                    .remove();
            return ResultData.success("取消关注成功");
        } else {
            Follow follow = new Follow();
            follow.setFollowerUid(followerUid);
            follow.setFollowingUid(followingUid);
            follow.setCreateTime(LocalDateTime.now());
            save(follow);
            return ResultData.success("关注成功");
        }
    }

    /**
     * 判断 followerUid 是否关注了 followingUid
     *
     * @param followerUid  关注者 uid
     * @param followingUid 被关注者 uid
     * @return true = 已关注，false = 未关注或参数为空
     */
    @Override
    public boolean isFollowing(Long followerUid, Long followingUid) {
        if (followerUid == null || followingUid == null) {
            return false;
        }
        return lambdaQuery()
                .eq(Follow::getFollowerUid, followerUid)
                .eq(Follow::getFollowingUid, followingUid)
                .one() != null;
    }

    /**
     * 查询指定用户的粉丝数和关注数
     *
     * @param uid 用户 uid
     * @return map 包含 followers、followings 两个字段
     */
    @Override
    public ResultData<HashMap<String, Long>> getFollowCount(Long uid) {
        HashMap<String, Long> result = new HashMap<>();
        long followers = lambdaQuery().eq(Follow::getFollowingUid, uid).count();
        long followings = lambdaQuery().eq(Follow::getFollowerUid, uid).count();
        result.put("followers", followers);
        result.put("followings", followings);
        return ResultData.success(result);
    }

    /**
     * 分页查询指定用户的粉丝列表（按关注时间倒序）
     *
     * @param uid        被关注者 uid
     * @param pageNum    页码，默认为 1
     * @param pageSize   每页数量，默认为 30
     * @param currentUid 当前登录用户 uid（可选，用于填充isFollowing字段）
     * @return 粉丝用户信息列表（包含isFollowing字段）
     */
    @Override
    public ResultData<List<UserDTO>> getFollowers(Long uid, Integer pageNum, Integer pageSize, Long currentUid) {
        pageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        pageSize = (pageSize == null || pageSize < 1) ? 30 : pageSize;
        int offset = (pageNum - 1) * pageSize;

        List<Long> followerUids = lambdaQuery()
                .select(Follow::getFollowerUid)
                .eq(Follow::getFollowingUid, uid)
                .orderByDesc(Follow::getCreateTime)
                .last("LIMIT " + offset + ", " + pageSize)
                .list()
                .stream()
                .map(Follow::getFollowerUid)
                .collect(Collectors.toList());

        if (followerUids.isEmpty()) {
            return ResultData.success(new ArrayList<>());
        }
        
        List<UserDTO> users = userService.getBatchUserInfo(followerUids);
        // 填充isFollowing字段
        fillFollowingStatus(users, currentUid);
        return ResultData.success(users);
    }

    /**
     * 分页查询指定用户的关注列表（按关注时间倒序）
     *
     * @param uid        关注者 uid
     * @param pageNum    页码，默认为 1
     * @param pageSize   每页数量，默认为 30
     * @param currentUid 当前登录用户 uid（可选，用于填充isFollowing字段）
     * @return 被关注用户信息列表（包含isFollowing字段）
     */
    @Override
    public ResultData<List<UserDTO>> getFollowings(Long uid, Integer pageNum, Integer pageSize, Long currentUid) {
        pageNum = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        pageSize = (pageSize == null || pageSize < 1) ? 30 : pageSize;
        int offset = (pageNum - 1) * pageSize;

        List<Long> followingUids = lambdaQuery()
                .select(Follow::getFollowingUid)
                .eq(Follow::getFollowerUid, uid)
                .orderByDesc(Follow::getCreateTime)
                .last("LIMIT " + offset + ", " + pageSize)
                .list()
                .stream()
                .map(Follow::getFollowingUid)
                .collect(Collectors.toList());

        if (followingUids.isEmpty()) {
            return ResultData.success(new ArrayList<>());
        }
        
        List<UserDTO> users = userService.getBatchUserInfo(followingUids);
        // 填充isFollowing字段
        fillFollowingStatus(users, currentUid);
        return ResultData.success(users);
    }

    /**
     * 批量填充用户的isFollowing字段
     *
     * @param users      用户列表
     * @param currentUid 当前登录用户 uid
     */
    private void fillFollowingStatus(List<UserDTO> users, Long currentUid) {
        if (users == null || users.isEmpty()) {
            return;
        }

        if (currentUid == null) {
            // 未登录，全部设为false
            for (UserDTO user : users) {
                user.setIsFollowing(false);
            }
            return;
        }

        // 批量查询当前用户对这些用户的关注状态
        List<Long> targetUids = users.stream()
                .map(UserDTO::getUid)
                .filter(targetUid -> !targetUid.equals(currentUid)) // 排除自己
                .collect(Collectors.toList());

        if (targetUids.isEmpty()) {
            for (UserDTO user : users) {
                user.setIsFollowing(false);
            }
            return;
        }

        // 查询当前用户关注的用户列表
        List<Long> myFollowingUids = lambdaQuery()
                .select(Follow::getFollowingUid)
                .eq(Follow::getFollowerUid, currentUid)
                .in(Follow::getFollowingUid, targetUids)
                .list()
                .stream()
                .map(Follow::getFollowingUid)
                .toList();

        // 填充isFollowing字段
        for (UserDTO user : users) {
            user.setIsFollowing(myFollowingUids.contains(user.getUid()));
        }
    }

    @Override
    public List<Long> getFollowingUids(Long uid) {
        if (uid == null) {
            return List.of();
        }
        return lambdaQuery()
                .select(Follow::getFollowingUid)
                .eq(Follow::getFollowerUid, uid)
                .list()
                .stream()
                .map(Follow::getFollowingUid)
                .collect(Collectors.toList());
    }
}
