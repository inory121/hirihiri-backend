package com.hiiro.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AccessControl {
    @Value("${internal.key}")
    private String internalKey;

    // 判断是否为内部请求
    public boolean isInternalRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        String flag = request.getHeader("X-Internal-Request");
        String key = request.getHeader("X-Internal-Key");
        return "true".equals(flag) && internalKey.equals(key);
    }
}