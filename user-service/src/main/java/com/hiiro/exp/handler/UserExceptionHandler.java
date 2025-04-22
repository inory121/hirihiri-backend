package com.hiiro.exp.handler;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.exp.UserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ResultData<String>> handleUserException(UserException e) {
        log.error("用户异常: {}", e.getMessage(), e);
        ResultData<String> resultData = ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, e.getMessage());
        return new ResponseEntity<>(resultData, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResultData<String>> handleAccessDeniedException(AccessDeniedException e) {
        log.error("权限拒绝异常: {}", e.getMessage(), e);
        ResultData<String> resultData = ResultData.fail(
                ResultCodeEnum.FORBIDDEN,
                "没有权限访问该资源"
        );
        return new ResponseEntity<>(resultData, HttpStatus.FORBIDDEN);
    }
}
