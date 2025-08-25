package com.hiiro.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 内部访问控制组件
 * 用于判断请求是否来自内部服务，防止外部直接访问内部接口
 */
@Component
public class AccessControl {
    @Value("${internal.key}")
    private String internalKey;

    /**
     * 判断当前请求是否为内部服务发起的请求
     * 
     * @return true表示是内部请求，false表示非内部请求
     */
    public boolean isInternalRequest() {
        // 获取当前请求上下文
        ServletRequestAttributes attributes = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        
        // 如果获取不到请求上下文，直接返回false
        if (attributes == null) {
            return false;
        }
        
        HttpServletRequest request = attributes.getRequest();

        // 获取请求头中的内部请求标识和密钥
        String flag = request.getHeader("X-Internal-Request");
        String key = request.getHeader("X-Internal-Key");
        
        // 只有当标识为"true"且密钥匹配时才认为是内部请求
        return "true".equals(flag) && internalKey.equals(key);
    }
}