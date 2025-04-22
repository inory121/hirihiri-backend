package com.hiiro.service.impl;

import com.hiiro.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {

    private User user;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 根据 User.role 字段返回权限
        return switch (user.getRole()) {
            case 0 -> // 普通用户
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
            case 1 -> // 管理员
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"));
            case 2 -> // 超级管理员
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            default -> Collections.emptyList();
        };
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getState() == 0;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getState() == 0;
    }
}
