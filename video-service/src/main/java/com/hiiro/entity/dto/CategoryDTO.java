package com.hiiro.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 分区表DTO
 * </p>
 *
 * @author hiiro
 * @since 2025-01-28
 */
@Data
@Tag(name = "CategoryDTO对象", description = "分区表DTO")
public class CategoryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主分区ID
     */
    @Schema(description = "主分区ID",name = "mcId")
    private String mcId;

    /**
     * 主分区名称
     */
    @Schema(description = "主分区名称",name = "mcName")
    private String mcName;

    /**
     * 主分区名称
     */
    @Schema(description = "子分区",name = "scList")
    private List<Map<String,Object>> scList;

}
