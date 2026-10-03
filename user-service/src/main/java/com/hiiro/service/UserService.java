package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.dto.RegisterDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.entity.dto.UserProfileDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param dto 注册请求 DTO（仅含 username/password/nickname）
     * @return ResultData对象
     */
    ResultData<String> register(RegisterDTO dto);

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
     * 更新当前登录用户的公开资料（字段白名单：昵称/头像/背景/性别/个性签名）
     * username 等敏感字段不在此接口可改范围内
     *
     * @param uid 当前登录用户 uid（由网关从 token 解析后注入，可信）
     * @param dto 资料更新请求
     * @return ResultData对象，data 为更新后的用户信息
     */
    ResultData<UserDTO> updateOwnProfile(Long uid, UserProfileDTO dto);

    /**
     * 用户登出
     *
     * @param uid   用户ID
     * @param token token
     * @return ResultData对象
     */
    ResultData<String> logout(String uid, String token);

    /**
     * 获取用户信息（String 版）
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    ResultData<UserDTO> getUserInfo(String uid);

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
     * @param keyword    关键词
     * @param pageNum    分页页数
     * @param pageSize   分页大小
     * @param order      排序方式：default-默认（相关度）、fan_desc-粉丝数由高到低、fan_asc-粉丝数由低到高、level_desc-等级由高到低、level_asc-等级由低到高
     * @param currentUid 当前登录用户uid（可选，用于填充isFollowing字段）
     * @return ResultData对象
     */
    ResultData<Map<String, Object>> searchUsers(String keyword, Integer pageNum, Integer pageSize, String order, Long currentUid);

    /**
     * 增加/减少用户硬币
     *
     * @param uid    用户id
     * @param amount 变化数量（正数增加，负数减少）
     * @return ResultData对象
     */
    ResultData<String> addCoin(Long uid, Double amount);

    /**
     * 增加投币经验值（每日上限50，返回实际增加量）
     *
     * @param uid          用户id
     * @param requestedGain 请求增加的经验值
     * @return 实际增加的经验值
     */
    ResultData<Integer> addCoinExp(Long uid, Integer requestedGain);

    /**
     * 增加经验值（按来源类型每日幂等，每天每类只发一次）
     *
     * @param uid    用户id
     * @param type   经验来源类型：login / watch / vip_watch / share / coin
     * @param amount 本次发放经验值
     * @return 实际增加的经验值（当天该类型已发过则返回 0）
     */
    ResultData<Integer> addExp(Long uid, String type, Integer amount);

    /**
     * 获取当前用户今日各类型每日奖励已获得经验（个人中心每日奖励卡片展示用）
     *
     * @param uid 当前登录用户 uid
     * @return ResultData对象，data 形如 {login:0|5, watch:0|5, vip_watch:0|10, share:0|5, coin:0..50}
     */
    ResultData<Map<String, Integer>> getDailyExp(Long uid);
}
