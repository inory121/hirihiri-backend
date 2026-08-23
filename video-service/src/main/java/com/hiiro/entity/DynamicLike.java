package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 动态点赞表
 * </p>
 *
 * @author hiiro
 * @since 2026-08-23
 */
@Data
@TableName("dynamic_like")
@Tag(name = "DynamicLike对象", description = "动态点赞表")
public class DynamicLike implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键ID", name = "id")
    private Long id;

    /**
     * 点赞用户ID
     */
    @Schema(description = "点赞用户ID", name = "uid")
    private Long uid;

    /**
     * 被点赞动态ID
     */
    @Schema(description = "被点赞动态ID", name = "dynamicId")
    private Long dynamicId;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createTime")
    private LocalDateTime createTime;
}
