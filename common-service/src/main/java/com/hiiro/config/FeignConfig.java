package com.hiiro.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

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
            }
        };
    }
}