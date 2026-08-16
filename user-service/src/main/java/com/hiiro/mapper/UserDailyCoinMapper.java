package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.UserDailyCoin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户每日登录硬币领取记录 Mapper
 */
@Mapper
public interface UserDailyCoinMapper extends BaseMapper<UserDailyCoin> {
}
