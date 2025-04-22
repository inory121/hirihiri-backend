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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 弹幕表
 * </p>
 *
 * @author hiiro
 * @since 2025-03-12
 */
@Data
@Tag(name = "Danmaku对象", description = "弹幕表")
public class Danmaku implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 弹幕ID
     */
    @Schema(name = "id",description = "弹幕ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 视频ID
     */
    @Schema(name = "vid",description = "视频ID")
    private Long vid;

    /**
     * 用户ID
     */
    @Schema(name = "uid",description = "用户ID")
    private Integer uid;

    /**
     * 弹幕内容
     */
    @Schema(name = "content",description = "弹幕内容")
    private String content;

    /**
     * 字体大小
     */
    @Schema(name = "fontsize",description = "字体大小")
    private Byte fontsize;

    /**
     * 弹幕模式 1从右到左(rtl) 2从左到右(ltr) 3顶部(top) 4底部(bottom)
     */
    @Schema(name = "mode",description = "弹幕模式 1从右到左(rtl) 2从左到右(ltr) 3顶部(top) 4底部(bottom)")
    private Byte mode;

    /**
     * 弹幕颜色 6位十六进制标准格式
     */
    @Schema(name = "color",description = "弹幕颜色 6位十六进制标准格式")
    private String color;

    /**
     * 弹幕所在视频的时间点
     */
    @Schema(name = "time",description = "弹幕所在视频的时间点")
    private BigDecimal time;

    /**
     * 弹幕状态 1默认过审 2被举报审核中 3删除
     */
    @Schema(name = "state",description = "弹幕状态 1默认过审 2被举报审核中 3删除")
    private Byte state;

    /**
     * 发送弹幕的日期时间
     */
    @Schema(name = "createDate",description = "发送弹幕的日期时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createDate;
}
