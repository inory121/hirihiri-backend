package com.hiiro.entity.dto;

import lombok.Data;

@Data
public class MessageNoticeCreateDTO {
    private Long receiveUid;
    private Long actorUid;
    private String noticeType;
    private String bizType;
    private Long bizId;
    private String title;
    private String contentSummary;
    private String extJson;
}
