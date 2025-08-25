package com.hiiro.filter;

import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

// Gateway全局过滤器
@Component
public class AuthFilter implements GlobalFilter, Ordered {
    @Resource
    private MyJwtUtil jwtUtil;

    @Resource
    private RedisUtil redisUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求头中的Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtil.verifyJwtToken(token)) {
                    // 解析Token
                    String uid = jwtUtil.getClaimFromToken(token, "uid");
                    // 解析token，拿到jti
                    Object jti = jwtUtil.getClaimFromToken(token, "jti");
                    //根据token的jti去redis判断是否在黑名单,如果是则不允许继续操作
                    Object blacklistJti = redisUtil.get("blacklist:user:" + uid + ":" + jti);
                    if (Objects.nonNull(blacklistJti)) {
                        throw new RuntimeException("token无效,用户已登出！");
                    }
                    // 添加UID到请求头
                    exchange.getRequest().mutate()
                            .header("uid", uid)
                            .header("token", token)
                            .build();
                }

            } catch (Exception e) {
                // Token解析失败处理
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级
    }
}
