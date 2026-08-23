package com.hiiro.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 动态列表返回 DTO
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
@Data
public class DynamicDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 动态ID
     */
    @Schema(description = "动态ID", name = "id")
    private Long id;

    /**
     * 发布者用户ID
     */
    @Schema(description = "发布者用户ID", name = "uid")
    private Long uid;

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
     * 关联视频ID
     */
    @Schema(description = "关联视频ID", name = "vid")
    private Long vid;

    /**
     * 图片URL列表
     */
    @Schema(description = "图片URL列表", name = "images")
    private List<String> images;

    /**
     * 是否置顶 0否 1是
     */
    @Schema(description = "是否置顶 0否 1是", name = "isTop")
    private Byte isTop;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createTime")
    private LocalDateTime createTime;

    /**
     * 发布者用户信息
     */
    @Schema(description = "发布者用户信息", name = "user")
    private UserDTO user;

    /**
     * 关联视频信息（视频动态时非空）
     */
    @Schema(description = "关联视频信息", name = "video")
    private Object video;

    /**
     * 被转发的原动态ID（转发动态时非空）
     */
    @Schema(description = "被转发的原动态ID", name = "parentId")
    private Long parentId;

    /**
     * 被转发的原动态完整数据（转发动态时非空）
     */
    @Schema(description = "被转发的原动态完整数据", name = "parent")
    private DynamicDTO parent;

    /**
     * 点赞数（列表查询时批量回填）
     */
    @Schema(description = "点赞数", name = "likeCount")
    private Integer likeCount;

    /**
     * 当前登录用户是否已点赞（列表查询时回填）
     */
    @Schema(description = "当前登录用户是否已点赞", name = "liked")
    private Boolean liked;

    /**
     * 评论数（列表查询时批量回填）
     */
    @Schema(description = "评论数", name = "commentCount")
    private Long commentCount;

    /**
     * 转发数（列表查询时批量回填，统计 parent_id 指向该动态的转发动态数）
     */
    @Schema(description = "转发数", name = "repostCount")
    private Long repostCount;
}
