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

    ResultData<HashMap<String, Long>> getFollowCount(Long uid);

    ResultData<List<UserDTO>> getFollowers(Long uid, Integer pageNum, Integer pageSize, Long currentUid);

    ResultData<List<UserDTO>> getFollowings(Long uid, Integer pageNum, Integer pageSize, Long currentUid);
}
