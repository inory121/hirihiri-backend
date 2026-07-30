package com.hiiro.entity;

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
 * <p>
 *
 * </p>
 *
 * @author hiiro
 * @since 2025-03-09
 */
@Data
@TableName("video_stat")
@Tag(name = "VideoStat对象", description = "视频统计数据表")
public class VideoStat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 视频ID
     */
    @TableId("vid")
    @Schema(description = "视频ID",name = "vid")
    private Long vid;

    /**
     * 播放数
     */
    @Schema(description = "播放数",name = "view")
    private Integer view;

    /**
     * 弹幕数
     */
    @Schema(description = "弹幕数",name = "danmaku")
    private Integer danmaku;

    /**
     * 评论数
     */
    @Schema(description = "评论数",name = "reply")
    private Integer reply;

    /**
     * 收藏数
     */
    @Schema(description = "收藏数",name = "favorite")
    private Integer favorite;

    /**
     * 投币数
     */
    @Schema(description = "投币数",name = "coin")
    private Integer coin;

    /**
     * 分享数
     */
    @Schema(description = "分享数",name = "share")
    private Integer share;

    /**
     * 获赞数
     */
    @TableField("`like`")
    @Schema(description = "获赞数",name = "like")
    private Integer like;

    /**
     * 点踩数
     */
    @Schema(description = "点踩数",name = "dislike")
    private Integer dislike;

    /**
     * 互动更新时间（热度任务用于增量筛选）
     */
    @Schema(description = "互动更新时间", name = "updateTime")
    private LocalDateTime updateTime;
}
