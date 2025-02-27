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
        String uid = request.getHeader("uid");
//        String token = request.getHeader("token");
        if (!StringUtils.hasText(uid)) {
            filterChain.doFilter(request, response);
            return;
        }
//        token = token.substring(7);
        //验证token是否合法
//        if (jwtUtil.verifyJwtToken(token)) {
        //解析token,拿到uid
//            Object uid = jwtUtil.getClaimFromToken(token, "uid");
//
//            // 解析token，拿到jti
//            Object jti = jwtUtil.getClaimFromToken(token, "jti");
//
//            //根据token的jti去redis判断是否在黑名单,如果是则不允许继续操作
//            Object blacklistJti = redisUtil.get("blacklist:user:" + uid + ":" + jti);
//            if (Objects.nonNull(blacklistJti)) {
//                throw new UserException(ResultCodeEnum.UNAUTHORIZED, "token无效,用户已登出！");
//            }
        //从redis中获取用户信息
        User loginUser = redisUtil.getObject("user:" + uid, User.class);

        // 如果redis查不到代表用户没登陆过或者token已过期
        if (Objects.isNull(loginUser)) {
            throw new UserException(ResultCodeEnum.UNAUTHORIZED, "用户未登录或token已过期！");
        }

        // 设置Spring Security上下文中的认证信息
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginUser, null, null);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        //放行
        filterChain.doFilter(request, response);
//        } else {
//            throw new UserException(ResultCodeEnum.UNAUTHORIZED, "token不合法");
//        }
    }
}
