package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户黑名单 —— 真正的"拉黑"关系（区别于推荐流的"屏蔽作者视频"）
 * blocker_uid 拉黑 blocked_uid 后，双方无法互发私信
 */
@Data
@Tag(name = "UserBlock对象", description = "用户黑名单表")
@TableName("user_block")
public class UserBlock implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "执行拉黑的用户uid")
    private Long blockerUid;

    @Schema(description = "被拉黑的用户uid")
    private Long blockedUid;

    @Schema(description = "拉黑时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
