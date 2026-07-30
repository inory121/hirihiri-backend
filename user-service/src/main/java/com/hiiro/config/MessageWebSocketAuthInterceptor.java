package com.hiiro.config;

import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
public class MessageWebSocketAuthInterceptor implements HandshakeInterceptor {

    @Resource
    private MyJwtUtil jwtUtil;

    @Resource
    private RedisUtil redisUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        String token = servletRequest.getServletRequest().getParameter("token");
        if (!StringUtils.hasText(token)) {
            return false;
        }

        try {
            if (!jwtUtil.verifyJwtToken(token)) {
                return false;
            }
            String uid = jwtUtil.getClaimFromToken(token, "uid");
            String jti = jwtUtil.getClaimFromToken(token, "jti");
            Optional<Object> blacklistJti = redisUtil.get("blacklist:user:" + uid + ":" + jti);
            if (blacklistJti.isPresent()) {
                return false;
            }
            attributes.put("uid", Long.parseLong(uid));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }
}
