package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;

import java.util.HashMap;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2025-01-29
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param user User实体
     * @return ResultData对象
     */
    ResultData<String> register(User user);

    /**
     * 用户登录
     *
     * @param user User实体
     * @return ResultData对象
     */
    ResultData<HashMap<String, Object>> login(User user);

    /**
     * 通过用户名获取用户信息
     *
     * @param username 用户名
     * @return user User实体
     */
    User getUserByUsername(String username);

    /**
     * 通过用户名获取用户信息
     *
     * @param uid 用户uid
     * @return user User实体
     */
    User getUserByUid(Long uid);

    /**
     * 更新用户信息
     *
     * @param user User实体
     * @return ResultData对象
     */
    int updateUserById(User user);

    /**
     * 用户登出
     *
     * @param uid 用户ID
     * @param token token
     * @return ResultData对象
     */
    ResultData<String> logout(String uid,String token);

    /**
     * 获取用户信息
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    ResultData<User> getUserInfo(String uid);

    /**
     * 通过用户ID获取用户名
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    ResultData<String> getUsernameByUid(String uid);
}
