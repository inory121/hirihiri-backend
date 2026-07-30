package com.hiiro.config;

import com.hiiro.handler.MessageWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class MessageWebSocketConfig implements WebSocketConfigurer {

    @Resource
    private MessageWebSocketHandler messageWebSocketHandler;

    @Resource
    private MessageWebSocketAuthInterceptor messageWebSocketAuthInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketHandler, "/ws/message")
                .addInterceptors(messageWebSocketAuthInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
