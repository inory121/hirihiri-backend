package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视频点踩记录表
 *
 * @author hiiro
 * @since 2025-06-26
 */
@Data
@TableName("video_dislike")
@Tag(name = "VideoDislike对象", description = "视频点踩记录表")
public class VideoDislike implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", name = "uid")
    private Long uid;

    /**
     * 视频ID
     */
    @Schema(description = "视频ID", name = "vid")
    private Long vid;

    /**
     * 点踩时间
     */
    @Schema(description = "点踩时间", name = "createTime")
    private LocalDateTime createTime;
}
