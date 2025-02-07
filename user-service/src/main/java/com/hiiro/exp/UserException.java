package com.hiiro.exp;

import com.hiiro.entity.ResultCodeEnum;

public class UserException extends GlobalException {

    public UserException(ResultCodeEnum resultCodeEnum) {
        super(resultCodeEnum);
    }

    public UserException(ResultCodeEnum resultCodeEnum, String message) {
        super(resultCodeEnum,message);
    }
}
