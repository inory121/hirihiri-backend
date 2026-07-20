package com.hiiro.entity;

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
 * 收藏夹表
 *
 * @author hiiro
 * @since 2025-06-23
 */
@Data
@TableName("favorite_folder")
@Tag(name = "FavoriteFolder对象", description = "收藏夹表")
public class FavoriteFolder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 收藏夹ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "收藏夹ID", name = "id")
    private Long id;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", name = "uid")
    private Long uid;

    /**
     * 收藏夹名称
     */
    @Schema(description = "收藏夹名称", name = "name")
    private String name;

    /**
     * 封面URL
     */
    @Schema(description = "封面URL", name = "coverUrl")
    private String coverUrl;

    /**
     * 描述
     */
    @Schema(description = "描述", name = "description")
    private String description;

    /**
     * 视频数量
     */
    @Schema(description = "视频数量", name = "videoCount")
    private Integer videoCount;

    /**
     * 是否默认收藏夹
     */
    @Schema(description = "是否默认收藏夹", name = "isDefault")
    private Boolean isDefault;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", name = "updateTime")
    private LocalDateTime updateTime;

    /**
     * 当前视频是否在该收藏夹中（仅查询时有效，不入库）
     */
    @TableField(exist = false)
    @Schema(description = "当前视频是否在该收藏夹中", name = "collected")
    private Boolean collected;
}
