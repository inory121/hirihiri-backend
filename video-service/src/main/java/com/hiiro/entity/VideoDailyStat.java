package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 视频每日统计数据表
 * 用于按天聚合曝光、点击、有效播放等指标
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Data
@TableName("video_daily_stat")
public class VideoDailyStat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "ID", name = "id")
    private Long id;

    @Schema(description = "视频ID", name = "vid")
    private Long vid;

    @Schema(description = "统计日期", name = "statDate")
    private LocalDate statDate;

    @Schema(description = "曝光次数", name = "exposureCount")
    private Integer exposureCount;

    @Schema(description = "点击次数", name = "clickCount")
    private Integer clickCount;

    @Schema(description = "有效播放次数", name = "validPlayCount")
    private Integer validPlayCount;

    @Schema(description = "总观看秒数", name = "totalWatchSeconds")
    private Integer totalWatchSeconds;

    @Schema(description = "完播次数", name = "finishCount")
    private Integer finishCount;

    @Schema(description = "点赞次数", name = "likeCount")
    private Integer likeCount;

    @Schema(description = "收藏次数", name = "favoriteCount")
    private Integer favoriteCount;

    @Schema(description = "投币次数", name = "coinCount")
    private Integer coinCount;

    @Schema(description = "点踩次数", name = "dislikeCount")
    private Integer dislikeCount;

    @Schema(description = "创建时间", name = "createTime")
    private LocalDateTime createTime;
}