package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.document.UserDocument;
import com.hiiro.entity.dto.UserDTO;

import java.util.HashMap;
import java.util.List;

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
    ResultData<HashMap<String, Object>> login(User user, Integer requiredRole);

    /**
     * 普通用户登录
     *
     * @param user User实体
     * @return ResultData对象
     */
    ResultData<HashMap<String, Object>> userLogin(User user);

    /**
     * 管理员登录
     *
     * @param user User实体
     * @return ResultData对象
     */
    ResultData<HashMap<String, Object>> adminLogin(User user);

    /**
     * 通过用户名获取用户信息
     *
     * @param username 用户名
     * @return user User实体
     */
    UserDTO getUserByUsername(String username);

    /**
     * 通过用户名获取用户信息
     *
     * @param uid 用户uid
     * @return user User实体
     */
    UserDTO getUserByUid(Long uid);

    /**
     * 更新用户信息
     *
     * @param user User实体
     * @return ResultData对象
     */
    ResultData<String> updateUserById(User user);

    /**
     * 用户登出
     *
     * @param uid   用户ID
     * @param token token
     * @return ResultData对象
     */
    ResultData<String> logout(String uid, String token);

    /**
     * 获取用户信息
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    ResultData<UserDTO> getUserInfo(String uid);

    /**
     * 通过用户ID获取用户名
     *
     * @param uid 用户ID
     * @return UserDTO对象
     */
    ResultData<UserDTO> getUserDTOByUid(Long uid);

    /**
     * 批量获取用户信息
     *
     * @param uids 用户ID集合
     * @return List<UserDTO>
     */
    List<UserDTO> getBatchUserInfo(List<Long> uids);

    /**
     * 获取用户分页信息
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return ResultData对象
     */
    ResultData<List<UserDTO>> getUserPage(Integer pageNum, Integer pageSize);

    /**
     * 搜索用户
     *
     * @param keyword  关键词
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    ResultData<List<UserDocument>> searchUsers(String keyword, Integer pageNum, Integer pageSize);
}
