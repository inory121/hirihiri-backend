package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 视频表
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
@Data
@Tag(name = "Video对象", description = "视频表")
public class Video implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 视频ID
     */
    @Schema(name = "视频ID")
    @TableId(value = "vid", type = IdType.AUTO)
    private Integer vid;

    /**
     * 投稿用户ID
     */
    @Schema(name = "投稿用户ID")
    private Integer uid;

    /**
     * 标题
     */
    @Schema(name = "标题")
    private String title;

    /**
     * 类型 1自制 2转载
     */
    @Schema(name = "类型 1自制 2转载")
    private Byte type;

    /**
     * 作者声明 0不声明 1未经允许禁止转载
     */
    @Schema(name = "作者声明 0不声明 1未经允许禁止转载")
    private Byte auth;

    /**
     * 播放总时长 单位秒
     */
    @Schema(name = "播放总时长 单位秒")
    private Double duration;

    /**
     * 主分区ID
     */
    @Schema(name = "主分区ID")
    private String mcId;

    /**
     * 子分区ID
     */
    @Schema(name = "子分区ID")
    private String scId;

    /**
     * 标签
     */
    @Schema(name = "标签")
    private String tags;

    /**
     * 简介
     */
    @Schema(name = "简介")
    private String descr;

    /**
     * 封面url
     */
    @Schema(name = "封面url")
    private String coverUrl;

    /**
     * 视频url
     */
    @Schema(name = "视频url")
    private String videoUrl;

    /**
     * 状态 0审核中 1已过审 2未通过 3已删除
     */
    @Schema(name = "状态 0审核中 1已过审 2未通过 3已删除")
    private Byte status;

    /**
     * 上传时间
     */
    @Schema(name = "上传时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime pubDate;

    /**
     * 删除时间
     */
    @Schema(name = "删除时间")
    private LocalDateTime delDate;
}
