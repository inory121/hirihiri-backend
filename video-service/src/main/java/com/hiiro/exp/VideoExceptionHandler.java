package com.hiiro.exp;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class VideoExceptionHandler {

    @ExceptionHandler(VideoNotFoundException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResultData<String> exception(Exception e) {
        log.error("全局异常信息:{}", e.getMessage(), e);
        return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR.getCode(), e.getMessage());
    }
}
