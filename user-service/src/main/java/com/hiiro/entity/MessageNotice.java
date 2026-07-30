package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "消息通知")
public class MessageNotice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long receiveUid;

    private Long actorUid;

    private String noticeType;

    private String bizType;

    private Long bizId;

    private String title;

    private String contentSummary;

    private Integer isRead;

    private String extJson;

    private LocalDateTime createTime;
}
