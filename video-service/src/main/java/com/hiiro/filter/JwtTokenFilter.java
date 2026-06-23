package com.hiiro.filter;

import com.alibaba.fastjson2.JSON;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    @Resource
    private MyJwtUtil jwtUtil;

    @Resource
    private RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
            throws ServletException, IOException {

        String uid = request.getHeader("uid");
        String token = request.getHeader("token");

        if (!StringUtils.hasText(uid) && !StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(uid) || !StringUtils.hasText(token)) {
            writeUnauthorized(response, "用户身份信息不完整");
            return;
        }

        if (!jwtUtil.verifyJwtToken(token)) {
            writeUnauthorized(response, "Token无效或已过期");
            return;
        }

        String tokenUid = jwtUtil.getClaimFromToken(token, "uid");
        if (!uid.equals(tokenUid)) {
            writeUnauthorized(response, "用户身份验证失败");
            return;
        }

        String jti = jwtUtil.getClaimFromToken(token, "jti");
        Optional<Object> blacklisted = redisUtil.get("blacklist:user:" + uid + ":" + jti);
        if (blacklisted.isPresent()) {
            writeUnauthorized(response, "Token已失效，请重新登录");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        ResultData<Void> result = ResultData.fail(ResultCodeEnum.UNAUTHORIZED, message);
        try (PrintWriter writer = response.getWriter()) {
            writer.write(JSON.toJSONString(result));
        }
    }
}
