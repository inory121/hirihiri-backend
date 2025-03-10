package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.UserDTOMapper;
import com.hiiro.mapper.UserMapper;
import com.hiiro.service.UserService;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-01-29
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    MyJwtUtil jwtUtil;

    @Resource
    RedisUtil redisUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserDTOMapper userDTOMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthenticationProvider authenticationProvider;

    /**
     * 用户注册
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<String> register(User user) {
        // 验证用户名是否已存在
        if (Objects.nonNull(getUserByUsername(user.getUsername()))) {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户已存在!");
        }
        // 加密用户密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // 尝试注册用户
        if (userMapper.insert(user) == 1) {
            // 如果前端没有传nickname则使用默认格式,有则使用前端传来的nickname
            if (Objects.isNull(user.getNickname())) {
                // 设置用户昵称,格式为"hiri_{用户uid}"
                user.setNickname("hiri_" + user.getUid());
                // 更新用户昵称
                if (updateUserById(user) != 1) {
                    return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "更新用户信息失败");
                }
            }
            return ResultData.success("注册成功，欢迎加入hirihiri！");
        } else {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户注册失败");
        }
    }

    /**
     * 用户登录
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<HashMap<String, Object>> login(User user) {
        // 创建一个UsernamePasswordAuthenticationToken对象，用于认证用户
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(), user.getPassword());
        // 使用authenticationProvider对提供的用户名和密码进行验证
        Authentication authenticate = authenticationProvider.authenticate(authenticationToken);
        // 获取认证通过后的用户详细信息
        UserDetailsImpl loginUser = (UserDetailsImpl) authenticate.getPrincipal();
        if (loginUser.getUser().getState() != 0) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "用户被封禁或已注销");
        }
        // 取出用户uid放入JWT令牌中
        Long uid = loginUser.getUser().getUid();
        // 创建默认的JWT令牌，其中包含用户的UID作为声明的一部分
        String token = jwtUtil.createDefaultJwtToken(new HashMap<>(Map.of("uid", uid)));
        // 登陆成功并成功更新登陆状态后将用户信息存入redis
        redisUtil.setObjectWithExpire("user:" + uid, loginUser.getUser(), TimeUnit.SECONDS);
        // 返回token和用户信息给前端
        return ResultData.success(
                new HashMap<String, Object>(
                        Map.of("user", BeanUtil.copyProperties(getUserByUid(uid), UserDTO.class), "token", token)),
                "登陆成功!");

    }

    /**
     * 通过用户名获取用户信息
     *
     * @param username 用户名
     * @return user User实体
     */
    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 通过用户ID获取用户信息
     *
     * @param uid 用户ID
     * @return user User实体
     */
    @Override
    public User getUserByUid(Long uid) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUid, uid));
    }

    /**
     * 更新用户信息
     *
     * @param user User实体
     * @return int
     */
    @Transactional
    @Override
    public int updateUserById(User user) {
        return userMapper.updateById(user);
    }

    /**
     * 用户登出
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<String> logout(String uid, String token) {
        if (!uid.isEmpty()) {
            // 从jwt中获取jti
            String jti = jwtUtil.getClaimFromToken(token, "jti");
            // 从redis中删除用户信息
            if (redisUtil.delete("user:" + uid, "token:user:" + uid)) {
                // 将token加入黑名单
                redisUtil.setWithExpire("blacklist:user:" + uid + ":" + jti, jti, TimeUnit.SECONDS);
                return ResultData.success("用户登出成功");
            }
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户已登出");
        }
        return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在或用户未登录");
    }

    /**
     * 获取用户信息
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Override
    public ResultData<User> getUserInfo(String uid) {
        try {
            // 通过uid获取用户信息
            Long userId = Long.valueOf(uid);
            if (Objects.nonNull(getUserByUid(userId))) {
                return ResultData.success(getUserByUid(userId), "获取用户信息成功");
            } else {
                return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
            }

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字符串无法转为 Long");
        }
    }

    /**
     * 通过用户ID获取用户名
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Override
    public UserDTO getUserDTOByUid(Long uid) {
        UserDTO user = userDTOMapper.selectOne(new LambdaQueryWrapper<UserDTO>().eq(UserDTO::getUid, uid));
        if (Objects.nonNull(user)) {
            return user;
        }
        return new UserDTO();
    }

    /**
     * 批量获取用户信息
     *
     * @param uids 用户ID列表
     * @return List<UserDTO>
     */
    @Override
    public List<UserDTO> getBatchUserInfo(List<Long> uids) {
        List<UserDTO> userList = userDTOMapper.selectByIds(uids);
        if (userList.isEmpty()) {
            return List.of();
        }
        return userList;
    }
}
