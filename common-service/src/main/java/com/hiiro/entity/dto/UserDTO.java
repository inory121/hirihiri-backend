package com.hiiro.entity.dto;

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
public class UserDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 用户账号
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像url
     */
    private String avatar;

    /**
     * 主页背景图url
     */
    private String background;

    /**
     * 性别 0私密 1男 2女
     */
    private Byte sex;

    /**
     * 个性签名
     */
    private String description;

    /**
     * 经验值
     */
    private Integer exp;

    /**
     * 硬币数
     */
    private Double coin;

    /**
     * 会员类型 0普通用户 1月度大会员 2年度大会员
     */
    private Byte vip;

    /**
     * 官方认证 0普通用户 1个人认证 2机构认证
     */
    private Byte auth;

    /**
     * 认证说明
     */
    private String authMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;
}
