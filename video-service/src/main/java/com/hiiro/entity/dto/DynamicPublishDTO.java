package com.hiiro.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 发布动态请求 DTO
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
@Data
public class DynamicPublishDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 动态标题
     */
    @Schema(description = "动态标题", name = "title")
    private String title;

    /**
     * 动态内容
     */
    @Schema(description = "动态内容", name = "content")
    private String content;

    /**
     * 类型 0普通动态(文字/图片) 1视频动态
     */
    @Schema(description = "类型 0普通动态 1视频动态", name = "type")
    private Byte type;

    /**
     * 关联视频ID（视频动态必填）
     */
    @Schema(description = "关联视频ID（视频动态必填）", name = "vid")
    private Long vid;

    /**
     * 图片URL列表
     */
    @Schema(description = "图片URL列表", name = "images")
    private List<String> images;

    /**
     * 被转发的原动态ID（转发动态时必填，type=3）
     */
    @Schema(description = "被转发的原动态ID（转发动态时必填）", name = "parentId")
    private Long parentId;
}
