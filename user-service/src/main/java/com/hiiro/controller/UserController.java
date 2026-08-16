package com.hiiro.controller;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.dto.RegisterDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author hiiro
 * @since 2025-01-29
 */
@Slf4j
@Tag(name = "用户信息管理")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @Operation(summary = "注册")
    @PostMapping("/register")
    public ResultData<String> register(@RequestBody RegisterDTO dto) {
        return userService.register(dto);
    }

    /**
     * @param user User实体
     * @return ResultData对象
     */
    @Operation(summary = "普通用户登录")
    @PostMapping("/login")
    public ResultData<HashMap<String, Object>> userLogin(@RequestBody User user) {
        return userService.userLogin(user);
    }

    /**
     * @param user User实体
     * @return ResultData对象
     */
    @Operation(summary = "管理员登录")
    @PostMapping("/admin/login")
    public ResultData<HashMap<String, Object>> adminLogin(@RequestBody User user) {
        return userService.adminLogin(user);
    }

    /**
     * @param uid   用户id
     * @param token token
     * @return ResultData对象
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ResultData<String> logout(@RequestHeader("uid") String uid, @RequestHeader("token") String token) {
        return userService.logout(uid, token);
    }

    /**
     * @param uid 用户id（从header获取）
     * @return ResultData对象
     */
    @Operation(summary = "获取单个用户信息")
    @GetMapping("/info")
    public ResultData<UserDTO> getUserInfo(@RequestHeader("uid") String uid) {
        Long uidLong = Long.valueOf(uid);
        // 每日登录奖励：已登录用户刷新页面/活跃时触发，由各自表按日幂等保证只发一次
        try {
            // Lv1+ 用户每日 +1 硬币（含等级门槛，失败不影响主流程）
            userService.grantDailyLoginCoin(uidLong);
            // 每日登录经验 +5（无等级门槛）
            userService.addExp(uidLong, "login", 5);
        } catch (Exception e) {
            log.warn("每日登录奖励发放失败: {}", e.getMessage());
        }
        return userService.getUserInfo(uid);
    }

    /**
     * @param uids 用户id
     * @return List<UserDTO>
     */
    @Operation(summary = "批量获取用户信息")
    @PostMapping("/batch/info")
    @PreAuthorize("@accessControl.isInternalRequest() || hasAnyRole('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public List<UserDTO> getBatchUserInfo(@RequestBody List<Long> uids) {
        return userService.getBatchUserInfo(uids);
    }

    /**
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Operation(summary = "获取用户DTO")
    @GetMapping("/info/{uid}")
    public ResultData<UserDTO> getUserByUid(@PathVariable("uid") Long uid) {
        UserDTO userDTO = userService.getUserByUid(uid);
        if (userDTO != null) {
            return ResultData.success(userDTO);
        }
        return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
    }

    @Operation(summary = "通过用户名获取用户DTO")
    @GetMapping("/by-username/{username}")
    public ResultData<UserDTO> getUserByUsername(@PathVariable("username") String username) {
        UserDTO userDTO = userService.getUserByUsername(username);
        if (userDTO != null) {
            return ResultData.success(userDTO);
        }
        return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
    }

    /**
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return ResultData对象
     */
    @Operation(summary = "分页获取用户信息")
    @GetMapping("/page")
    @PreAuthorize("hasRole('ROLE_ADMIN')||hasRole('ROLE_SUPER_ADMIN')")
    public ResultData<List<UserDTO>> getUserPage(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                 @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return userService.getUserPage(pageNum, pageSize);
    }

    /**
     * 增加/减少用户硬币
     *
     * @param uid    用户id
     * @param amount 变化数量（正数增加，负数减少）
     * @return ResultData对象
     */
    @Operation(summary = "增加/减少用户硬币")
    @PostMapping("/coin/add")
    @PreAuthorize("@accessControl.isInternalRequest()")
    public ResultData<String> addCoin(@RequestParam("uid") Long uid,
                                      @RequestParam("amount") Double amount) {
        return userService.addCoin(uid, amount);
    }

    /**
     * 增加投币经验值（每日上限50）
     *
     * @param uid           用户id
     * @param requestedGain 请求增加的经验值
     * @return ResultData对象
     */
    @Operation(summary = "增加投币经验值（每日上限50）")
    @PostMapping("/coin/exp/add")
    @PreAuthorize("@accessControl.isInternalRequest()")
    public ResultData<Integer> addCoinExp(@RequestParam("uid") Long uid,
                                          @RequestParam("requestedGain") Integer requestedGain) {
        return userService.addCoinExp(uid, requestedGain);
    }

    /**
     * @param uid    用户id
     * @param type   经验来源类型：login / watch / vip_watch / share / coin
     * @param amount 本次发放经验值
     * @return ResultData对象
     */
    @Operation(summary = "增加经验值（按来源类型每日幂等，每天每类只发一次）")
    @PostMapping("/exp/add")
    @PreAuthorize("@accessControl.isInternalRequest()")
    public ResultData<Integer> addExp(@RequestParam("uid") Long uid,
                                      @RequestParam("type") String type,
                                      @RequestParam("amount") Integer amount) {
        return userService.addExp(uid, type, amount);
    }

    /**
     * @param user User实体
     * @return ResultData对象
     */
    @Operation(summary = "更新用户信息")
    @PostMapping("/update")
    @PreAuthorize("@accessControl.isInternalRequest() || hasRole('ROLE_SUPER_ADMIN')")
    public ResultData<String> updateUserById(@RequestBody User user) {
        return userService.updateUserById(user);
    }

    /**
     * 搜索用户
     *
     * @param keyword  关键词
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @param order    排序方式
     * @param request  HTTP请求对象
     * @return ResultData对象
     */
    @Operation(summary = "搜索用户")
    @GetMapping("/search")
    public ResultData<Map<String, Object>> searchUsers(@RequestParam("keyword") String keyword,
                                                       @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                       @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                       @RequestParam(name = "order", required = false) String order,
                                                       HttpServletRequest request) {
        if (keyword == null || keyword.trim().isEmpty() || keyword.length() > 50) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "搜索词长度必须在1-50字符之间");
        }
        Long currentUid = null;
        String uidStr = request.getHeader("uid");
        if (uidStr != null && !uidStr.isEmpty()) {
            try {
                currentUid = Long.valueOf(uidStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return userService.searchUsers(keyword.trim(), pageNum, pageSize, order, currentUid);
    }

}
