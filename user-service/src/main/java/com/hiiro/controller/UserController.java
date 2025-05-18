package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author hiiro
 * @since 2025-01-29
 */
@Tag(name = "用户信息管理")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * @param user User实体
     * @return ResultData对象
     */
    @Operation(summary = "注册")
    @PostMapping("/register")
    public ResultData<String> register(@RequestBody User user) {
        return userService.register(user);
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
     * @param uid 用户id
     * @return ResultData对象
     */
    @Operation(summary = "获取单个用户信息")
    @GetMapping("/info")
    public ResultData<UserDTO> getUserInfo(@RequestHeader("uid") String uid) {
        return userService.getUserInfo(uid);
    }

    /**
     * @param uids 用户id
     * @return List<UserDTO>
     */
    @Operation(summary = "获取批量用户信息")
    @PostMapping("/batch/info")
    @PreAuthorize("@accessControl.isInternalRequest() || hasAnyRole('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public List<UserDTO> getBatchUserInfo(@RequestBody List<Long> uids) {
        return userService.getBatchUserInfo(uids);
    }

    /**
     * @param uid 用户id
     * @return UserDTO
     */
    @Operation(summary = "获取用户DTO")
    @GetMapping("/info/{uid}")
    @PreAuthorize("@accessControl.isInternalRequest() || hasAnyRole('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResultData<UserDTO> getUserByUid(@PathVariable("uid") Long uid) {
        return userService.getUserDTOByUid(uid);
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
     * @param user User实体
     * @return ResultData对象
     */
    @Operation(summary = "更新用户信息")
    @PostMapping("/update")
    public ResultData<String> updateUserById(@RequestBody User user) {
        return userService.updateUserById(user);
    }

}
