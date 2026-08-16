package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日经验记录（通用，按来源类型每日幂等）
 * <p>
 * exp_type 取值：coin(投币，每日上限50) / login / watch / vip_watch / share
 */
@Data
@TableName("user_exp_daily")
public class UserExpDaily implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long uid;

    /**
     * 日期
     */
    private LocalDate date;

    /**
     * 经验来源类型：coin / login / watch / vip_watch / share
     */
    private String expType;

    /**
     * 当日该来源获得的经验值
     */
    private Integer expGain;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
