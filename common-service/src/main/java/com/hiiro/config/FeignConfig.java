package com.hiiro.config;

import feign.Logger;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    @Value("${internal.key}")
    private String internalKey;

    @Bean
    public RequestInterceptor authHeaderInterceptor() {
        return template -> {
            // 内部请求标识和密钥始终添加，不依赖请求上下文（异步线程也能正确标记）
            template.header("X-Internal-Request", "true");
            template.header("X-Internal-Key", internalKey);

            // 从当前请求上下文中获取网关传递的头部（异步线程可能无上下文，此时不透传）
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String uid = request.getHeader("uid");
                String token = request.getHeader("token");
                if (uid != null) template.header("uid", uid);
                if (token != null) template.header("token", token);
            }
        };
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

}