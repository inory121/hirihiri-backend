package com.hiiro.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 发过动态的UP主返回 DTO
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
@Data
public class DynamicUpDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * UP主用户ID
     */
    @Schema(description = "UP主用户ID", name = "uid")
    private Long uid;

    /**
     * 动态数量
     */
    @Schema(description = "动态数量", name = "dynamicCount")
    private Long dynamicCount;

    /**
     * 最近发动态时间
     */
    @Schema(description = "最近发动态时间", name = "latestTime")
    private LocalDateTime latestTime;

    /**
     * UP主用户信息
     */
    @Schema(description = "UP主用户信息", name = "user")
    private UserDTO user;
}
