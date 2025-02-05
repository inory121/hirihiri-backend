package com.hiiro.entity;

import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;

@Getter
@ToString
public enum ResultCodeEnum {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求错误"),
    UNAUTHORIZED(401, "未授权"),
    NOT_FOUND(404, "资源未找到"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    TOKEN_INVALID(40000,"token不合法"),
    INVALID_PARAM(40001, "参数无效"),
    USER_NOT_EXIST(40002, "用户不存在"),
    VIDEO_NOT_EXIST(40003, "视频不存在"),
    PERMISSION_DENIED(40004, "权限不足"),
    USERNAME_OR_PASSWORD_ERROR(40005,"用户名或密码错误"),
    USER_BANNED_OR_DELETED(40006,"用户被封禁或已注销"),
    DATABASE_INSERT_ERROR(50001, "数据库插入失败"), //
    DATABASE_UPDATE_ERROR(50002, "数据库更新失败"), //
    DATABASE_DELETE_ERROR(50003, "数据库删除失败"), //
    DATABASE_SELECT_ERROR(50004, "数据库查询失败"); //
    private final int code;
    private final String message;

    ResultCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ResultCodeEnum getResultCodeEnum(int code) {
        return Arrays.stream(ResultCodeEnum.values()).filter(e -> e.getCode() == code)
                .findFirst().orElse(null);
    }
}
