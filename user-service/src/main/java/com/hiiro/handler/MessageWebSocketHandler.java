package com.hiiro.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiiro.entity.dto.MessageSendDTO;
import com.hiiro.entity.dto.MessageSocketRequest;
import com.hiiro.service.MessageService;
import com.hiiro.service.MessageSocketSessionRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private MessageService messageService;

    @Resource
    private MessageSocketSessionRegistry messageSocketSessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long uid = getUid(session);
        if (uid != null) {
            messageSocketSessionRegistry.addSession(uid, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long uid = getUid(session);
        if (uid == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        MessageSocketRequest request = objectMapper.readValue(message.getPayload(), MessageSocketRequest.class);
        if (request == null || request.getType() == null) {
            return;
        }

        switch (request.getType()) {
            case "SEND_PRIVATE_MESSAGE" -> {
                MessageSendDTO dto = new MessageSendDTO();
                dto.setTargetUid(request.getTargetUid());
                dto.setContent(request.getContent());
                messageService.sendPrivateMessage(uid, dto);
            }
            case "READ_SESSION" -> {
                if (request.getSessionId() != null) {
                    messageService.markSessionRead(uid, request.getSessionId());
                }
            }
            case "PING" -> session.sendMessage(new TextMessage("{\"type\":\"PONG\",\"data\":null}"));
            default -> log.debug("忽略未知消息类型: {}", request.getType());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long uid = getUid(session);
        if (uid != null) {
            messageSocketSessionRegistry.removeSession(uid, session);
        }
    }

    private Long getUid(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object uid = attributes.get("uid");
        if (uid instanceof Long longUid) {
            return longUid;
        }
        if (uid instanceof Integer intUid) {
            return Long.valueOf(intUid);
        }
        return null;
    }
}
