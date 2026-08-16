package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日登录硬币领取记录
 */
@Data
@TableName("user_coin_daily")
public class UserDailyCoin implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 领取日期
     */
    private LocalDate date;

    /**
     * 是否已领取 0否 1是
     */
    private Integer granted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
