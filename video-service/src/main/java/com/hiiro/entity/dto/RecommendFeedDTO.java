package com.hiiro.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 推荐流响应 DTO
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Data
@Schema(description = "推荐流响应")
public class RecommendFeedDTO {

    @Schema(description = "推荐视频列表")
    private List<Map<String, Object>> items;

    @Schema(description = "下一页游标，空表示没有更多")
    private String nextCursor;

    @Schema(description = "请求ID（用于曝光归因）")
    private String requestId;
}