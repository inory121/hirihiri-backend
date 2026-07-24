package com.hiiro.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 全局 CORS 过滤器
 * <p>
 * Spring Cloud Gateway 的 globalcors 配置在下游服务不可用或返回错误时，
 * 错误响应不会携带 CORS 头，导致浏览器报 CORS 错误。
 * 此过滤器确保所有响应（包括错误响应）都携带 CORS 头，
 * 并直接处理 OPTIONS 预检请求，避免预检被其他过滤器拦截。
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements WebFilter {

    private static final String ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String ALLOWED_HEADERS = "Authorization, Content-Type, X-Requested-With, uid, token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        HttpHeaders headers = response.getHeaders();

        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin != null) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ALLOWED_METHODS);
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOWED_HEADERS);
        }

        // 直接处理 OPTIONS 预检请求，不转发到下游
        if (request.getMethod() == HttpMethod.OPTIONS) {
            response.setStatusCode(HttpStatus.OK);
            return Mono.empty();
        }

        return chain.filter(exchange);
    }
}
