package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

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
    @Operation(summary = "登录")
    @PostMapping("/login")
    public ResultData<HashMap<String, Object>> login(@RequestBody User user) {
        return userService.login(user);
    }

    /**
     *
     * @param authorization 认证信息
     * @return ResultData对象
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ResultData<String> logout(@RequestHeader(name = "Authorization") String authorization) {
        return userService.logout(authorization);
    }

    /**
     *
     * @param authorization 认证信息
     * @return ResultData对象
     */
    @Operation(summary = "获取用户信息")
    @PostMapping("/info")
    public ResultData<User> getUserInfo(@RequestHeader(name = "Authorization") String authorization) {
        return userService.getUserInfo(authorization);
    }

}
