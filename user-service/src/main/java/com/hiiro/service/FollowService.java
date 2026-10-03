package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Follow;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.UserDTO;

import java.util.HashMap;
import java.util.List;

public interface FollowService extends IService<Follow> {

    ResultData<String> toggleFollow(Long followerUid, Long followingUid);

    boolean isFollowing(Long followerUid, Long followingUid);

    /**
     * 删除两个用户之间任意方向的关注关系（用于拉黑时自动双向取关）
     *
     * @param uidA 用户A uid
     * @param uidB 用户B uid
     */
    void removeFollowBetween(Long uidA, Long uidB);

    ResultData<HashMap<String, Long>> getFollowCount(Long uid);

    ResultData<List<UserDTO>> getFollowers(Long uid, Integer pageNum, Integer pageSize, Long currentUid);

    ResultData<List<UserDTO>> getFollowings(Long uid, Integer pageNum, Integer pageSize, Long currentUid);

    /**
     * 获取用户关注的作者 uid 列表
     *
     * @param uid 关注者 uid
     * @return 被关注者 uid 列表
     */
    List<Long> getFollowingUids(Long uid);

    /**
     * 获取关注指定用户的粉丝 uid 列表（内部调用，用于投稿通知等场景）
     *
     * @param uid 被关注者 uid
     * @return 粉丝 uid 列表
     */
    List<Long> getFollowerUids(Long uid);
}
