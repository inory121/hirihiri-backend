package com.hiiro.entity.dto;

import lombok.Data;

@Data
public class MessageSocketRequest {
    private String type;
    private Long targetUid;
    private Long sessionId;
    private String content;
}
