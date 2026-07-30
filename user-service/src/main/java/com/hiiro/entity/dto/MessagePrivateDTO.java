package com.hiiro.entity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessagePrivateDTO {
    private Long id;
    private Long sessionId;
    private Long senderUid;
    private Long receiverUid;
    private String content;
    private String contentType;
    private Integer isRead;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
    private UserDTO senderUser;
}
