package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户黑名单（真拉黑）—— 所有接口均需登录，网关注入 uid 请求头
 */
@Tag(name = "用户黑名单管理")
@RestController
@RequestMapping("/api/user/block")
public class UserBlockController {

    @Resource
    private UserBlockService userBlockService;

    /**
     * 获取当前用户的黑名单列表
     *
     * @param uid      当前登录用户 uid（从请求头获取）
     * @param pageNum  页码（可选，默认 1）
     * @param pageSize 每页数量（可选，默认 30）
     * @return 被拉黑用户信息列表
     */
    @Operation(summary = "获取黑名单列表")
    @GetMapping("/list")
    public ResultData<List<UserDTO>> getBlockList(@RequestHeader("uid") String uid,
                                                  @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                  @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return userBlockService.getBlockList(Long.parseLong(uid), pageNum, pageSize);
    }

    /**
     * 拉黑用户
     *
     * @param uid  当前登录用户 uid（从请求头获取）
     * @param targetUid 被拉黑用户 uid
     * @return 操作结果提示信息
     */
    @Operation(summary = "拉黑用户")
    @PostMapping("/{targetUid}")
    public ResultData<String> blockUser(@RequestHeader("uid") String uid,
                                        @PathVariable("targetUid") Long targetUid) {
        return userBlockService.blockUser(Long.parseLong(uid), targetUid);
    }

    /**
     * 取消拉黑
     *
     * @param uid  当前登录用户 uid（从请求头获取）
     * @param targetUid 被取消拉黑用户 uid
     * @return 操作结果提示信息
     */
    @Operation(summary = "取消拉黑")
    @DeleteMapping("/{targetUid}")
    public ResultData<String> unblockUser(@RequestHeader("uid") String uid,
                                          @PathVariable("targetUid") Long targetUid) {
        return userBlockService.unblockUser(Long.parseLong(uid), targetUid);
    }

    /**
     * 查询当前用户是否已拉黑目标用户（供前端按钮状态回显）
     *
     * @param uid  当前登录用户 uid（从请求头获取）
     * @param targetUid 目标用户 uid
     * @return true = 已在黑名单
     */
    @Operation(summary = "查询是否已拉黑")
    @GetMapping("/status/{targetUid}")
    public ResultData<Boolean> getBlockStatus(@RequestHeader("uid") String uid,
                                              @PathVariable("targetUid") Long targetUid) {
        return ResultData.success(userBlockService.isBlocked(Long.parseLong(uid), targetUid));
    }

    /**
     * 查询当前用户与目标用户之间的双向拉黑关系（供 space 页互访拦截）
     *
     * @param uid       当前登录用户 uid（从请求头获取）
     * @param targetUid 目标用户 uid
     * @return { blockedByMe, blockingMe }
     */
    @Operation(summary = "查询双向拉黑关系")
    @GetMapping("/relation/{targetUid}")
    public ResultData<Map<String, Boolean>> getBlockRelation(@RequestHeader("uid") String uid,
                                                             @PathVariable("targetUid") Long targetUid) {
        return ResultData.success(userBlockService.getRelation(Long.parseLong(uid), targetUid));
    }

    /**
     * 内部接口：获取指定用户拉黑的全部 uid（供 video-service 隐藏被拉黑者评论）
     *
     * @param uid 目标用户 uid
     * @return 被拉黑 uid 列表
     */
    @Operation(summary = "获取黑名单uid列表（内部）")
    @GetMapping("/blocked-uids/{uid}")
    public ResultData<List<Long>> getBlockedUids(@PathVariable("uid") Long uid) {
        return ResultData.success(userBlockService.getBlockedUids(uid));
    }

    /**
     * 内部接口：判断 blockerUid 是否拉黑了 blockedUid（供 video-service 写路径拦截）
     *
     * @param blockerUid  拉黑发起人 uid
     * @param blockedUid  被拉黑用户 uid
     * @return true = 已拉黑
     */
    @Operation(summary = "判断单向拉黑关系（内部）")
    @GetMapping("/is-blocked")
    public ResultData<Boolean> isBlocked(@RequestParam("blockerUid") Long blockerUid,
                                         @RequestParam("blockedUid") Long blockedUid) {
        return ResultData.success(userBlockService.isBlocked(blockerUid, blockedUid));
    }
}
