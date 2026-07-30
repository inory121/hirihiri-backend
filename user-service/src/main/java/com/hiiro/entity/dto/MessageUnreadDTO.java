package com.hiiro.entity.dto;

import lombok.Data;

@Data
public class MessageUnreadDTO {
    private int totalUnread;
    private int privateUnread;
    private int strangerUnread;
    private int replyUnread;
    private int atUnread;
    private int likeUnread;
    private int systemUnread;
}
