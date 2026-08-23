package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
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
 * 用户动态表
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
@Data
@TableName("`dynamic`")
@Tag(name = "Dynamic对象", description = "用户动态表")
public class Dynamic implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 动态ID
     */
    @Schema(description = "动态ID", name = "id")
    @TableId(value = "id", type = IdType.AUTO)
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
     * 图片URL列表(JSON数组)
     */
    @Schema(description = "图片URL列表(JSON数组)", name = "images")
    private String images;

    /**
     * 是否置顶 0否 1是
     */
    @Schema(description = "是否置顶 0否 1是", name = "isTop")
    private Byte isTop;

    /**
     * 被转发的原动态ID（转发动态时非空）
     */
    @Schema(description = "被转发的原动态ID（转发动态时非空）", name = "parentId")
    private Long parentId;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createTime")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 点赞数（列表查询时批量回填，非表字段）
     */
    @Schema(description = "点赞数", name = "likeCount")
    @TableField(exist = false)
    private Integer likeCount;

    /**
     * 当前登录用户是否已点赞（列表查询时回填，非表字段）
     */
    @Schema(description = "当前登录用户是否已点赞", name = "liked")
    @TableField(exist = false)
    private Boolean liked;

    /**
     * 转发数（列表查询时批量回填，非表字段）
     */
    @Schema(description = "转发数", name = "repostCount")
    @TableField(exist = false)
    private Long repostCount;
}
