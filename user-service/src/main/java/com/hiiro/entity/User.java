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
    @Schema(description = "用户ID")
    private Long uid;

    /**
     * 用户账号
     */
    @Schema(description = "用户账号",name = "username")
    private String username;

    /**
     * 用户密码
     */
    @Schema(description = "用户密码",name = "password")
    private String password;

    /**
     * 用户昵称
     */
    @Schema(description = "用户昵称",name = "nickname")
    private String nickname;

    /**
     * 用户头像url
     */
    @Schema(description = "用户头像url",name = "avatar")
    private String avatar;

    /**
     * 主页背景图url
     */
    @Schema(description = "主页背景图url",name = "background")
    private String background;

    /**
     * 性别 0私密 1男 2女
     */
    @Schema(description = "性别 0私密 1男 2女",name = "sex")
    private Byte sex;

    /**
     * 个性签名
     */
    @Schema(description = "个性签名",name = "description")
    private String description;

    /**
     * 经验值
     */
    @Schema(description = "经验值",name = "exp")
    private Integer exp;

    /**
     * 硬币数
     */
    @Schema(description = "硬币数",name = "coin")
    private Double coin;

    /**
     * 会员类型 0普通用户 1月度大会员 2年度大会员
     */
    @Schema(description = "会员类型 0普通用户 1月度大会员 2年度大会员",name = "vip")
    private Byte vip;

    /**
     * 状态 0正常 1封禁 2注销
     */
    @Schema(description = "状态 0正常 1封禁 2注销",name = "state")
    private Byte state;

    /**
     * 角色类型 0普通用户 1管理员 2超级管理员
     */
    @Schema(description = "角色类型 0普通用户 1管理员 2超级管理员",name = "role")
    private Byte role;

    /**
     * 官方认证 0普通用户 1个人认证 2机构认证
     */
    @Schema(description = "官方认证 0普通用户 1个人认证 2机构认证",name = "auth")
    private Byte auth;

    /**
     * 认证说明
     */
    @Schema(description = "认证说明",name = "authMsg")
    private String authMsg;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间",name = "createDate")
    @TableField(fill = FieldFill.INSERT) //mybatisplus会在插入后自动添加该字段
    private LocalDateTime createDate;

    /**
     * 注销时间
     */
    @Schema(description = "注销时间",name = "deleteDate")
    private LocalDateTime deleteDate;

}
