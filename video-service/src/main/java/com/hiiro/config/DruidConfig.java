package com.hiiro.config;

import com.alibaba.druid.support.jakarta.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class DruidConfig {

    /**
     * 手动注册 Druid 监控页面的 Servlet
     * 此举等同于在 application.yml 中配置 stat-view-servlet
     */
    @Bean
    public ServletRegistrationBean<StatViewServlet> druidStatViewServlet() {
        ServletRegistrationBean<StatViewServlet> registrationBean = new ServletRegistrationBean<>();
        registrationBean.setServlet(new StatViewServlet());
        // 配置访问路径，必须和你访问的路径一致
        registrationBean.addUrlMappings("/druid/*");

        // 初始化参数（监控页面的配置）
        Map<String, String> initParameters = new HashMap<>();
        initParameters.put("loginUsername", "admin"); // 监控页面登录用户名
        initParameters.put("loginPassword", "admin123"); // 监控页面登录密码
        initParameters.put("resetEnable", "false"); // 禁用HTML页面上的“Reset All”功能
        // initParameters.put("allow", "127.0.0.1"); // 允许访问的IP，默认允许所有
        // initParameters.put("deny", "192.168.1.100"); // 拒绝访问的IP

        registrationBean.setInitParameters(initParameters);
        return registrationBean;
    }

    /**
     * 配置监控过滤器 - 这是解决SQL监控为空的关键！
     * 这个过滤器负责收集SQL执行数据
     */
    @Bean
    public FilterRegistrationBean<WebStatFilter> druidWebStatFilter() {
        FilterRegistrationBean<WebStatFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new WebStatFilter());

        // 配置过滤器路径
        registrationBean.addUrlPatterns("/*");

        // 排除一些静态资源和监控页面本身的请求
        Map<String, String> initParameters = new HashMap<>();
        initParameters.put("exclusions", "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*");
        registrationBean.setInitParameters(initParameters);

        return registrationBean;
    }

}