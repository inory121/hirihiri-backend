package com.hiiro.entity.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageNoticeDTO {
    private Long id;
    private Long receiveUid;
    private String noticeType;
    private String bizType;
    private Long bizId;
    private String title;
    private String contentSummary;
    private Integer isRead;
    private String extJson;
    private LocalDateTime createTime;
    private UserDTO actorUser;
}
