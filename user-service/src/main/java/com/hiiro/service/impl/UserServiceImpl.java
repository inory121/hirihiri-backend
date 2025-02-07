package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.exp.UserException;
import com.hiiro.mapper.UserMapper;
import com.hiiro.service.UserService;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
            throw new UserException(ResultCodeEnum.DATABASE_INSERT_ERROR, "用户已存在!");
        }
        // 设置创建日期
        user.setCreateDate(DateUtil.date().toLocalDateTime());
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
                    throw new UserException(ResultCodeEnum.DATABASE_UPDATE_ERROR, "更新用户信息失败");
                }
            }
            return ResultData.success("注册成功，欢迎加入hirihiri！");
        } else {
            throw new UserException(ResultCodeEnum.DATABASE_INSERT_ERROR, "用户注册失败");
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
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword());
        // 使用authenticationProvider对提供的用户名和密码进行验证
        Authentication authenticate = authenticationProvider.authenticate(authenticationToken);
        // 获取认证通过后的用户详细信息
        UserDetailsImpl loginUser = (UserDetailsImpl) authenticate.getPrincipal();
        if (loginUser.getUser().getState() != 0) {
            throw new UserException(ResultCodeEnum.USER_BANNED_OR_DELETED, ResultCodeEnum.USER_BANNED_OR_DELETED.getMessage());
        }
        // 将用户ID（UID）转换为字符串形式，以便放入JWT令牌中
        String uid = String.valueOf(loginUser.getUser().getUid());
        // 创建默认的JWT令牌，其中包含用户的UID作为声明的一部分
        String token = jwtUtil.createDefaultJwtToken(new HashMap<>(Map.of("uid", uid)));
        // 更新用户登录状态
        if (updateUserById(loginUser.getUser().getUid()) == 1) {
            // 登陆成功并成功更新登陆状态后将用户信息存入redis
            redisUtil.setWithExpire("user:" + uid, loginUser.getUser(), TimeUnit.SECONDS);
            // 返回token和用户信息给前端
            return ResultData.success(new HashMap<String, Object>(Map.of("user", BeanUtil.copyProperties(getUserByUsername(loginUser.getUsername()), UserDTO.class), "token", token)), "登陆成功!");
        } else {
            throw new UserException(ResultCodeEnum.DATABASE_UPDATE_ERROR, "更新用户登陆状态失败!");
        }

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
     * 通过用户uid更新用户信息
     *
     * @param uid 用户实体
     */
    @Transactional
    @Override
    public int updateUserById(Long uid) {
        return userMapper.update(new LambdaUpdateWrapper<User>().eq(User::getUid, uid).set(User::getIsLogin, true));
    }

    @Transactional
    @Override
    public int updateUserById(User user) {
        return userMapper.updateById(user);
    }

    @Override
    public ResultData<String> logout(String authorization) {
        //从SecurityContextHolder获取用户信息
        User loginUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (Objects.nonNull(loginUser)) {
            //从请求头获取Authorization并提取token
            String token = authorization.substring(7);
            //从jwt中获取jti
            Object jti = jwtUtil.getClaimFromToken(token, "jti");
            //删除redis存储的用户信息和token
            if (redisUtil.delete("user:" + loginUser.getUid(), "token:user:" + loginUser.getUid())) {
                //把已删除的token的jti存入黑名单,防止token过期前有人继续用旧token
                redisUtil.setWithExpire("blacklist:user:" + loginUser.getUid() + ":" + jti, jti, TimeUnit.SECONDS);
                return ResultData.success("用户登出成功");
            }
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户已登出");
        }
        return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户未登录");
    }

}
