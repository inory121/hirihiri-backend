package com.hiiro.filter;

import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    @Resource
    private MyJwtUtil jwtUtil;

    @Resource
    private RedisUtil redisUtil;

    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/user/register",
            "/api/user/login",
            "/api/user/admin/login",
            "/api/user/info/",
            "/api/user/page",
            "/api/user/search",
            "/api/video/recommend",
            "/api/video/all",
            "/api/video/",
            "/api/video/search",
            "/api/video/user/",
            "/api/comment/video/",
            "/api/danmaku/video/",
            "/api/category/get/all",
            "/api/follow/status/",
            "/api/follow/count/",
            "/api/follow/followers/",
            "/api/follow/followings/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        requestBuilder.headers(h -> {
            h.remove("X-Internal-Request");
            h.remove("X-Internal-Key");
            h.remove("uid");
            h.remove("token");
        });

        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        boolean hasToken = StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ");

        if (isPublic && !hasToken) {
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        if (!hasToken) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            if (!jwtUtil.verifyJwtToken(token)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String uid = jwtUtil.getClaimFromToken(token, "uid");
            String jti = jwtUtil.getClaimFromToken(token, "jti");

            Optional<Object> blacklistJti = redisUtil.get("blacklist:user:" + uid + ":" + jti);
            if (blacklistJti.isPresent()) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            requestBuilder.header("uid", uid).header("token", token);
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
