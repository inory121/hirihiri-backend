package com.hiiro.exp;

import com.hiiro.entity.ResultCodeEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class GlobalException extends RuntimeException {

    private final int code;
    private final String message;

    public GlobalException(ResultCodeEnum resultCodeEnum) {
        this.code = resultCodeEnum.getCode();
        this.message = resultCodeEnum.getMessage();
        log.error("错误信息:{},错误码:{}",message,code);
    }

    public GlobalException(ResultCodeEnum resultCodeEnum,String message) {
        this.code = resultCodeEnum.getCode();
        this.message = message;
        log.error("错误信息:{},错误码:{}",message,code);
    }
}
