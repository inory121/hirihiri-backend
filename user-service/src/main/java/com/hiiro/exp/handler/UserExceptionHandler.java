package com.hiiro.exp.handler;

import com.hiiro.entity.ResultData;
import com.hiiro.exp.UserException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(UserException.class)
    public ResponseEntity<ResultData<String>> handleUserException(UserException e) {
        ResultData<String> resultData = ResultData.fail(e.getCode(), e.getMessage());
        return new ResponseEntity<>(resultData, HttpStatus.INTERNAL_SERVER_ERROR);
    }

//    @ExceptionHandler(AuthenticationException.class)
//    public ResponseEntity<ResultData<String>> handleAuthenticationException() {
//        ResultData<String> resultData = ResultData.fail(ResultCodeEnum.UNAUTHORIZED.getCode(), "用户名或密码验证失败!");
//        return new ResponseEntity<>(resultData, HttpStatus.UNAUTHORIZED);
//    }

}
