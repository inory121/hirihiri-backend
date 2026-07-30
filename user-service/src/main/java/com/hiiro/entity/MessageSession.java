package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "私信会话")
public class MessageSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long uidLow;

    private Long uidHigh;

    private String lastMessage;

    private LocalDateTime lastMessageTime;

    private Integer unreadLow;

    private Integer unreadHigh;

    /** uid_low 用户是否已删除(隐藏)会话：0=正常，1=已删除 */
    private Integer deletedLow;

    /** uid_high 用户是否已删除(隐藏)会话：0=正常，1=已删除 */
    private Integer deletedHigh;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
