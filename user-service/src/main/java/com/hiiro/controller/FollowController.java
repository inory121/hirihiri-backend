package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@Tag(name = "关注/粉丝管理")
@RestController
@RequestMapping("/api/follow")
public class FollowController {

    @Resource
    private FollowService followService;

    /**
     * 关注 / 取消关注目标用户
     *
     * @param myUid         当前登录用户 uid（从请求头获取）
     * @param followingUid  被关注者 uid（URL 路径参数）
     * @return 操作结果提示信息
     */
    @Operation(summary = "关注/取消关注")
    @PostMapping("/toggle/{uid}")
    public ResultData<String> toggleFollow(@RequestHeader("uid") String myUid,
                                           @PathVariable("uid") Long followingUid) {
        return followService.toggleFollow(Long.parseLong(myUid), followingUid);
    }

    /**
     * 查询当前登录用户是否关注了目标用户
     *
     * @param myUid         当前登录用户 uid
     * @param followingUid  目标用户 uid
     * @return true = 已关注，false = 未关注
     */
    @Operation(summary = "查询是否已关注")
    @GetMapping("/status/{uid}")
    public ResultData<Boolean> getFollowStatus(@RequestHeader("uid") String myUid,
                                               @PathVariable("uid") Long followingUid) {
        return ResultData.success(followService.isFollowing(Long.parseLong(myUid), followingUid));
    }

    /**
     * 获取指定用户的粉丝数与关注数
     *
     * @param uid 用户 uid
     * @return map 包含 followers、followings 两个字段
     */
    @Operation(summary = "获取粉丝数/关注数")
    @GetMapping("/count/{uid}")
    public ResultData<HashMap<String, Long>> getFollowCount(@PathVariable("uid") Long uid) {
        return followService.getFollowCount(uid);
    }

    /**
     * 分页获取指定用户的粉丝列表
     *
     * @param uid      被关注者 uid
     * @param pageNum  页码（可选，默认 1）
     * @param pageSize 每页数量（可选，默认 30）
     * @return 粉丝用户信息列表（包含isFollowing字段）
     */
    @Operation(summary = "获取粉丝列表")
    @GetMapping("/followers/{uid}")
    public ResultData<List<UserDTO>> getFollowers(@PathVariable("uid") Long uid,
                                                  @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                  @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                  @RequestHeader(value = "uid", required = false) String myUid) {
        Long currentUid = (myUid != null && !myUid.isEmpty()) ? Long.parseLong(myUid) : null;
        return followService.getFollowers(uid, pageNum, pageSize, currentUid);
    }

    /**
     * 分页获取指定用户的关注列表
     *
     * @param uid      关注者 uid
     * @param pageNum  页码（可选，默认 1）
     * @param pageSize 每页数量（可选，默认 30）
     * @return 被关注用户信息列表（包含isFollowing字段）
     */
    @Operation(summary = "获取关注列表")
    @GetMapping("/followings/{uid}")
    public ResultData<List<UserDTO>> getFollowings(@PathVariable("uid") Long uid,
                                                   @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                   @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                   @RequestHeader(value = "uid", required = false) String myUid) {
        Long currentUid = (myUid != null && !myUid.isEmpty()) ? Long.parseLong(myUid) : null;
        return followService.getFollowings(uid, pageNum, pageSize, currentUid);
    }
}
