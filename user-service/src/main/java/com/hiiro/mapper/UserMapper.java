package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.User;
import org.apache.ibatis.annotations.Mapper;


/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2025-01-29
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}

