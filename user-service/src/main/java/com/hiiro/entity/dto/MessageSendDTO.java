package com.hiiro.entity.dto;

import lombok.Data;

@Data
public class MessageSendDTO {
    private Long targetUid;
    private String content;
}
