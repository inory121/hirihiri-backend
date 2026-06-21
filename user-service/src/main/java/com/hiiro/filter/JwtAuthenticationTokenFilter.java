package com.hiiro.filter;

import com.hiiro.entity.User;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.service.impl.UserDetailsImpl;
import com.hiiro.utils.MyJwtUtil;
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
import cn.hutool.core.bean.BeanUtil;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    @Resource
    RedisUtil redisUtil;

    @Resource
    MyJwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {

        String uid = request.getHeader("uid");
        String token = request.getHeader("token");

        if (!StringUtils.hasText(uid) || !StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtil.verifyJwtToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String tokenUid = jwtUtil.getClaimFromToken(token, "uid");
        if (!uid.equals(tokenUid)) {
            filterChain.doFilter(request, response);
            return;
        }

        String jti = jwtUtil.getClaimFromToken(token, "jti");
        Optional<Object> blacklisted = redisUtil.get("blacklist:user:" + uid + ":" + jti);
        if (blacklisted.isPresent()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<UserDTO> loginUserOpt = redisUtil.getObject("user:" + uid, UserDTO.class);
        if (loginUserOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        User loginUser = BeanUtil.copyProperties(loginUserOpt.get(), User.class);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(loginUser, null,
                        new UserDetailsImpl(loginUser).getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
