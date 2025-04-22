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
    @Schema(description = "视频ID",name = "vid")
    @TableId(value = "vid", type = IdType.AUTO)
    private Long vid;

    /**
     * 投稿用户ID
     */
    @Schema(description = "投稿用户ID",name = "uid")
    private Long uid;

    /**
     * 标题
     */
    @Schema(description = "标题",name = "title")
    private String title;

    /**
     * 类型 1自制 2转载
     */
    @Schema(description = "类型 1自制 2转载",name = "type")
    private Byte type;

    /**
     * 作者声明 0不声明 1未经允许禁止转载
     */
    @Schema(description = "作者声明 0不声明 1未经允许禁止转载",name = "auth")
    private Byte auth;

    /**
     * 播放总时长 单位秒
     */
    @Schema(description = "播放总时长 单位秒",name = "duration")
    private Double duration;

    /**
     * 主分区ID
     */
    @Schema(description = "主分区ID",name = "mcId")
    private String mcId;

    /**
     * 子分区ID
     */
    @Schema(description = "子分区ID",name = "scId")
    private String scId;

    /**
     * 标签
     */
    @Schema(description = "标签",name = "tags")
    private String tags;

    /**
     * 简介
     */
    @Schema(description = "简介",name = "descr")
    private String descr;

    /**
     * 封面url
     */
    @Schema(description = "封面url",name = "coverUrl")
    private String coverUrl;

    /**
     * 视频url
     */
    @Schema(description = "视频url",name = "videoUrl")
    private String videoUrl;

    /**
     * 状态 0审核中 1已过审 2未通过 3已删除
     */
    @Schema(description = "状态 0审核中 1已过审 2未通过 3已删除",name = "status")
    private Byte status;

    /**
     * 上传时间
     */
    @Schema(description = "上传时间",name = "createDate")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    /**
     * 删除时间
     */
    @Schema(description = "删除时间",name = "delDate")
    private LocalDateTime delDate;
}
