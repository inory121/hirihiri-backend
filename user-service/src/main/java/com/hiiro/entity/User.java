package com.hiiro.entity;

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
@Tag(name = "User对象", description = "用户表")
public class User implements Serializable {

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
     * 用户密码
     */
    @Schema(name = "用户密码")
    private String password;

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
     * 状态 0正常 1封禁 2注销
     */
    @Schema(name = "状态 0正常 1封禁 2注销")
    private Byte state;

    /**
     * 角色类型 0普通用户 1管理员 2超级管理员
     */
    @Schema(name = "角色类型 0普通用户 1管理员 2超级管理员")
    private Byte role;

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
//    @JsonSerialize(using = LocalDateTimeSerializer.class)
//    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createDate;

    /**
     * 注销时间
     */
    @Schema(name = "注销时间")
//    @JsonSerialize(using = LocalDateTimeSerializer.class)
//    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deleteDate;

    /**
     * 是否登录 0未登录 1已登录
     */
    @Schema(name = "是否登录 0未登录 1已登录")
    private Boolean isLogin;
}
