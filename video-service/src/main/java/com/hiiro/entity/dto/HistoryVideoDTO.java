package com.hiiro.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 浏览历史-视频信息 DTO
 * </p>
 *
 * @author hiiro
 * @since 2025-06-15
 */
@Data
@Schema(description = "浏览历史-视频信息DTO")
public class HistoryVideoDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 浏览历史ID
     */
    @Schema(description = "浏览历史ID", name = "id")
    private Integer id;

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
     * 视频标题
     */
    @Schema(description = "视频标题", name = "title")
    private String title;

    /**
     * 视频封面URL
     */
    @Schema(description = "视频封面URL", name = "coverUrl")
    private String coverUrl;

    /**
     * 视频时长(秒)
     */
    @Schema(description = "视频时长(秒)", name = "duration")
    private Double duration;

    /**
     * 视频作者ID
     */
    @Schema(description = "视频作者ID", name = "authorUid")
    private Integer authorUid;

    /**
     * 视频作者用户名
     */
    @Schema(description = "视频作者用户名", name = "authorUsername")
    private String authorUsername;
}