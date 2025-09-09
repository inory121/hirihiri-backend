package com.hiiro.filter;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.User;
import com.hiiro.exp.UserException;
import com.hiiro.service.impl.UserDetailsImpl;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Resource
    RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {
        //获取网关传递的uid
        String uid = request.getHeader("uid");
        if (!StringUtils.hasText(uid)) {
            filterChain.doFilter(request, response);
            return;
        }
        //从redis中获取用户信息
        Optional<User> loginUserOpt = redisUtil.getObject("user:" + uid, User.class);

        // 如果redis查不到代表用户没登陆过或者token已过期
        UsernamePasswordAuthenticationToken authentication = getAuthenticationToken(loginUserOpt);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        //放行
        filterChain.doFilter(request, response);

    }

    private static UsernamePasswordAuthenticationToken getAuthenticationToken(Optional<User> loginUserOpt) {
        if (loginUserOpt.isEmpty()) {
            throw new UserException(ResultCodeEnum.UNAUTHORIZED, "用户未登录或token已过期！");
        }

        User loginUser = loginUserOpt.get();

        // 创建 UserDetailsImpl 并设置到 Security 上下文中
        UserDetailsImpl userDetails = new UserDetailsImpl(loginUser);
        // 设置Spring Security上下文中的认证信息
        return new UsernamePasswordAuthenticationToken(loginUser, null, userDetails.getAuthorities());
    }
}