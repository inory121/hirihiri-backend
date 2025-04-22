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
            // 从当前请求上下文中获取网关传递的头部
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                // 透传网关添加的认证头
                template.header("uid", request.getHeader("uid"));
                template.header("token", request.getHeader("token"));
                // 标记为内部请求
                template.header("X-Internal-Request", "true");
                template.header("X-Internal-Key", internalKey); // 验证密钥
            }
        };
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

}