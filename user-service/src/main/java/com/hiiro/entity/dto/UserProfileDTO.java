package com.hiiro.entity.dto;

import lombok.Data;

/**
 * 个人资料更新 DTO —— 只暴露用户可自助编辑的公开资料字段
 * 不含 username（登录账号与 @提及锚点，唯一索引，禁止自助修改）、
 * password / role / state / auth / exp / coin / vip 等敏感字段
 */
@Data
public class UserProfileDTO {

    /**
     * 昵称（对外展示名）
     */
    private String nickname;

    /**
     * 头像url
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
}
