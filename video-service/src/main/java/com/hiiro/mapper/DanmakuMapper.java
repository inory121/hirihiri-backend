package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.Danmaku;
import org.apache.ibatis.annotations.Mapper;


/**
 * <p>
 * 弹幕表 Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2025-03-12
 */
@Mapper
public interface DanmakuMapper extends BaseMapper<Danmaku> {

}

