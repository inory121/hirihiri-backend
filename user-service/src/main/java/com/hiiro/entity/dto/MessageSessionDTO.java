package com.hiiro.entity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageSessionDTO {
    private Long sessionId;
    private UserDTO peerUser;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
    private Boolean following;
}
