package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.User;
import com.hiiro.exp.UserException;
import com.hiiro.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Resource
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        // 使用 Optional 处理可能为 null 的 user 对象，并创建 UserDetails 实例或抛出异常
        return Optional.ofNullable(user)
                .map(UserDetailsImpl::new) // 将 user 转换为 UserDetails 实例
                .orElseThrow(() -> new UserException(ResultCodeEnum.USER_NOT_EXIST)); // 如果 user 为 null，则抛出异常
    }
}
