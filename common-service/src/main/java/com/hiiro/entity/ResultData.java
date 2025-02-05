package com.hiiro.entity;

import lombok.Data;

@Data
public class ResultData<T> {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public ResultData() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ResultData<T> success(T data) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(ResultCodeEnum.SUCCESS.getCode());
        resultData.setMessage(ResultCodeEnum.SUCCESS.getMessage());
        resultData.setData(data);
        return resultData;
    }

    public static <T> ResultData<T> success(T data,String message) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(ResultCodeEnum.SUCCESS.getCode());
        resultData.setMessage(message);
        resultData.setData(data);
        return resultData;
    }

    public static <T> ResultData<T> fail(int code, String message) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(code);
        resultData.setMessage(message);
        return resultData;
    }
}
