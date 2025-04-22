package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 * 分区表
 * </p>
 *
 * @author hiiro
 * @since 2025-01-28
 */
@Data
@Tag(name = "Category对象", description = "分区表")
public class Category implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分区唯一ID
     */

    @TableId(value = "cid", type = IdType.AUTO)
    @Schema(description = "分区唯一ID",name = "cid")
    private Integer cId;

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
     * 主分区名称
     */
    @Schema(description = "主分区名称",name = "mcName")
    private String mcName;

    /**
     * 子分区名称
     */
    @Schema(description = "子分区名称",name = "scName")
    private String scName;

    /**
     * 描述
     */
    @Schema(description = "描述",name = "descr")
    private String descr;

    /**
     * 推荐标签
     */
    @Schema(description = "推荐标签",name = "rcmTag")
    private String rcmTag;
}
