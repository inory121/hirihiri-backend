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

    /**
     * 动态未读数（关注的UP主新投稿，noticeType=dynamic）。
     * 仅用于头部动态入口红点，不计入 totalUnread，也不在消息中心展示。
     */
    private int dynamicUnread;
}
