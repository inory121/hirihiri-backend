package com.hiiro.service;

import org.springframework.web.socket.WebSocketSession;

public interface MessageSocketSessionRegistry {
    void addSession(Long uid, WebSocketSession session);

    void removeSession(Long uid, WebSocketSession session);
}
