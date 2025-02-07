package com.hiiro.exp.handler;

import com.hiiro.entity.ResultCodeEnum;
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
        ResultData<String> resultData = ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, e.getMessage());
        return new ResponseEntity<>(resultData, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
