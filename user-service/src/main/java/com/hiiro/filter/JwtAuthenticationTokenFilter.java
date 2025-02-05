package com.hiiro.filter;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.User;
import com.hiiro.exp.UserException;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
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
import java.util.Objects;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Resource
    MyJwtUtil jwtUtil;

    @Resource
    RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //获取token
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        token = token.substring(7);
        //解析token
        if (jwtUtil.verifyJwtToken(token)) {
            Object uid = jwtUtil.getClaimFromToken(token, "uid");
            //从redis中获取用户信息
            User loginUser = redisUtil.getObject("user:" + uid, User.class);
            if (Objects.isNull(loginUser)) {
                // 如果redis查不到代表用户没登陆过或者redis出错了
                throw new UserException(ResultCodeEnum.UNAUTHORIZED, "用户未登录");
            }
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(loginUser, null, null);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            //放行
            filterChain.doFilter(request, response);
        } else {
            throw new UserException(ResultCodeEnum.TOKEN_INVALID);
        }

    }
}
