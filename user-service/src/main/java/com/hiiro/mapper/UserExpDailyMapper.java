package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.UserExpDaily;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户每日经验记录 Mapper
 */
@Mapper
public interface UserExpDailyMapper extends BaseMapper<UserExpDaily> {
}
