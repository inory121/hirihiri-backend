package com.hiiro.entity;

import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;

@Getter
@ToString
public enum ResultCodeEnum {

    // ====== HTTP 标准状态码 ======
    SUCCESS(200, "操作成功"),
    CREATED(201, "资源创建成功"),        
    NO_CONTENT(204, "无返回内容"),      
    BAD_REQUEST(400, "请求错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "权限不足"),          
    NOT_FOUND(404, "资源未找到"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"), 
    TOO_MANY_REQUESTS(429, "请求过于频繁"),   
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),

    // ====== 自定义状态码 ======
    USER_NOT_EXIST(4001, "用户不存在");

    // ====== 数据库状态码 ======
    // INTERNAL_SERVER_ERROR(50001, "数据库插入失败"), 
    // INTERNAL_SERVER_ERROR(50002, "数据库更新失败"), 
    // DATABASE_DELETE_ERROR(50003, "数据库删除失败"), 
    // DATABASE_SELECT_ERROR(50004, "数据库查询失败"),

    // ====== Redis状态码 ======
    // REDIS_INSERT_ERROR(60001, "Redis插入失败"), 
    // REDIS_UPDATE_ERROR(60002, "Redis更新失败"), 
    // REDIS_DELETE_ERROR(60003, "Redis删除失败"), 
    // REDIS_SELECT_ERROR(60004, "Redis查询失败"); 
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
