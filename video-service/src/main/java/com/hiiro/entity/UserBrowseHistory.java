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
 * 用户浏览历史表
 * </p>
 *
 * @author hiiro
 * @since 2025-06-15
 */
@Data
@TableName("user_browse_history")
@Tag(name = "UserBrowseHistory对象", description = "用户浏览历史表")
public class UserBrowseHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键ID", name = "id")
    private Integer id;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", name = "uid")
    private Integer uid;

    /**
     * 视频ID
     */
    @Schema(description = "视频ID", name = "vid")
    private Integer vid;

    /**
     * 浏览时间
     */
    @Schema(description = "浏览时间", name = "browseTime")
    private LocalDateTime browseTime;

    /**
     * 播放进度(秒)
     */
    @Schema(description = "播放进度(秒)", name = "progress")
    private Integer progress;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createDate")
    private LocalDateTime createDate;
}
