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
    @Schema(name = "分区唯一ID")
    private Integer cId;

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
     * 主分区名称
     */
    @Schema(name = "主分区名称")
    private String mcName;

    /**
     * 子分区名称
     */
    @Schema(name = "子分区名称")
    private String scName;

    /**
     * 描述
     */
    @Schema(name = "描述")
    private String descr;

    /**
     * 推荐标签
     */
    @Schema(name = "推荐标签")
    private String rcmTag;
}
