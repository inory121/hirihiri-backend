package com.hiiro.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author hiiro
 * @since 2025-01-29
 */
@Data
@Tag(name = "UserDTO对象", description = "用户表")
public class UserDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(value = "uid", type = IdType.AUTO)
    @Schema(name = "用户ID")
    private Long uid;

    /**
     * 用户账号
     */
    @Schema(name = "用户账号")
    private String username;

    /**
     * 用户昵称
     */
    @Schema(name = "用户昵称")
    private String nickname;

    /**
     * 用户头像url
     */
    @Schema(name = "用户头像url")
    private String avatar;

    /**
     * 主页背景图url
     */
    @Schema(name = "主页背景图url")
    private String background;

    /**
     * 性别 0私密 1男 2女
     */
    @Schema(name = "性别 0私密 1男 2女")
    private Byte sex;

    /**
     * 个性签名
     */
    @Schema(name = "个性签名")
    private String description;

    /**
     * 经验值
     */
    @Schema(name = "经验值")
    private Integer exp;

    /**
     * 硬币数
     */
    @Schema(name = "硬币数")
    private Double coin;

    /**
     * 会员类型 0普通用户 1月度大会员 2年度大会员
     */
    @Schema(name = "会员类型 0普通用户 1月度大会员 2年度大会员")
    private Byte vip;

    /**
     * 官方认证 0普通用户 1个人认证 2机构认证
     */
    @Schema(name = "官方认证 0普通用户 1个人认证 2机构认证")
    private Byte auth;

    /**
     * 认证说明
     */
    @Schema(name = "认证说明")
    private String authMsg;

    /**
     * 创建时间
     */
    @Schema(name = "创建时间")
    private LocalDateTime createDate;
}
