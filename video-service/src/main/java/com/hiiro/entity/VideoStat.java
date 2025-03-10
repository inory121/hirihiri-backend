package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

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
    @Schema(name = "视频ID")
    private Long vid;

    /**
     * 播放数
     */
    @Schema(name = "播放数")
    private Integer view;

    /**
     * 弹幕数
     */
    @Schema(name = "弹幕数")
    private Integer danmaku;

    /**
     * 评论数
     */
    @Schema(name = "评论数")
    private Integer reply;

    /**
     * 收藏数
     */
    @Schema(name = "收藏数")
    private Integer favorite;

    /**
     * 投币数
     */
    @Schema(name = "投币数")
    private Integer coin;

    /**
     * 分享数
     */
    @Schema(name = "分享数")
    private Integer share;

    /**
     * 获赞数
     */
    @TableField("`like`")
    @Schema(name = "获赞数")
    private Integer like;

    /**
     * 点踩数
     */
    @Schema(name = "点踩数")
    private Integer dislike;
}
