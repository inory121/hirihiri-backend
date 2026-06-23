package com.hiiro.filter;

import com.hiiro.config.SecurityProperties;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    @Resource
    private MyJwtUtil jwtUtil;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private SecurityProperties securityProperties;

    private final List<PathPattern> publicPathPatterns = new ArrayList<>();

    @PostConstruct
    public void init() {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        List<String> publicPaths = securityProperties.getPublicPaths();
        if (publicPaths != null) {
            for (String pattern : publicPaths) {
                if (StringUtils.hasText(pattern)) {
                    publicPathPatterns.add(parser.parse(pattern.trim()));
                }
            }
        }
    }

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

        boolean isPublic = isPublicPath(path);

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        boolean hasToken = StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ");

        // 公开路径：无论有无token都放行，有token则尝试解析uid供下游使用
        if (isPublic) {
            if (hasToken) {
                String token = authHeader.substring(7);
                try {
                    if (jwtUtil.verifyJwtToken(token)) {
                        String uid = jwtUtil.getClaimFromToken(token, "uid");
                        String jti = jwtUtil.getClaimFromToken(token, "jti");
                        Optional<Object> blacklistJti = redisUtil.get("blacklist:user:" + uid + ":" + jti);
                        if (blacklistJti.isEmpty()) {
                            requestBuilder.header("uid", uid).header("token", token);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 非公开路径：必须有有效token
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

    /**
     * 判断请求路径是否为公开路径
     * 使用 Spring PathPattern 匹配，支持通配符和正则变量，规则统一在配置文件维护
     */
    private boolean isPublicPath(String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (PathPattern pattern : publicPathPatterns) {
            if (pattern.matches(pathContainer)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
