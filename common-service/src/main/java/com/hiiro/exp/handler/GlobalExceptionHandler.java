package com.hiiro.exp.handler;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResultData<String> exception(Exception e) {
        log.error("全局异常信息:{}", e.getMessage(), e);
        return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, e.getMessage());
    }
}
