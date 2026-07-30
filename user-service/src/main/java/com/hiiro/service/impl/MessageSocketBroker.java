package com.hiiro.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiiro.entity.dto.MessageSocketEnvelope;
import com.hiiro.service.MessageSocketSessionRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MessageSocketBroker implements MessageSocketSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByUid = new ConcurrentHashMap<>();

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public void addSession(Long uid, WebSocketSession session) {
        sessionsByUid.computeIfAbsent(uid, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void removeSession(Long uid, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUid.get(uid);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUid.remove(uid);
        }
    }

    public void sendToUser(Long uid, String type, Object data) {
        Set<WebSocketSession> sessions = sessionsByUid.get(uid);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(new MessageSocketEnvelope(type, data));
        } catch (Exception e) {
            log.error("序列化消息失败, uid={}", uid, e);
            return;
        }

        TextMessage message = new TextMessage(payload);
        sessions.removeIf(session -> !session.isOpen());
        for (WebSocketSession session : sessions) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("推送消息失败, uid={}, sessionId={}", uid, session.getId(), e);
            }
        }
    }
}
