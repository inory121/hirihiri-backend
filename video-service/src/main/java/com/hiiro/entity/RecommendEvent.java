package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 推荐行为事件流水表
 * 用于记录曝光、点击、观看进度、点踩等推荐相关事件
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Data
@TableName("recommend_event")
public class RecommendEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "自增ID", name = "id")
    private Long id;

    /**
     * 客户端生成的全局唯一事件ID（用于幂等）
     */
    @Schema(description = "事件唯一ID（客户端 UUID）", name = "eventId")
    private String eventId;

    /**
     * 用户ID（未登录为null）
     */
    @Schema(description = "用户ID", name = "uid")
    private Long uid;

    /**
     * 视频ID
     */
    @Schema(description = "视频ID", name = "vid")
    private Long vid;

    /**
     * 事件类型：impression/click/watch_progress/dislike
     */
    @Schema(description = "事件类型", name = "eventType")
    private String eventType;

    /**
     * 推荐请求ID（用于归因）
     */
    @Schema(description = "请求ID", name = "requestId")
    private String requestId;

    /**
     * 场景：home/related/search
     */
    @Schema(description = "场景", name = "scene")
    private String scene;

    /**
     * 在推荐列表中的位置（从0开始）
     */
    @Schema(description = "位置", name = "position")
    private Integer position;

    /**
     * 观看秒数
     */
    @Schema(description = "观看秒数", name = "watchSeconds")
    private Integer watchSeconds;

    /**
     * 观看进度比例（0-1）
     */
    @Schema(description = "观看进度", name = "progressRatio")
    private BigDecimal progressRatio;

    /**
     * 事件时间
     */
    @Schema(description = "事件时间", name = "eventTime")
    private LocalDateTime eventTime;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createTime")
    private LocalDateTime createTime;
}