package com.hiiro.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /**
     * 公开路径列表，从 application.yml 的 security.public-paths 读取
     * 支持 Spring PathPattern 语法：
     *   /api/video/recommend        精确匹配
     *   /api/user/info/**           匹配任意子路径
     *   /api/video/{vid:[0-9]+}     仅匹配数字变量（避免匹配到 upload 等敏感子路径）
     */
    private List<String> publicPaths = new ArrayList<>();

}
