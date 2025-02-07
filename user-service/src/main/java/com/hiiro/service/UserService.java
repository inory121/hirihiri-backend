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

    ResultData<String> register(User user);

    ResultData<HashMap<String,Object>> login(User user);

    User getUserByUsername(String username);

    int updateUserById(Long uid);

    int updateUserById(User user);

    ResultData<String> logout(String authorization);
}
