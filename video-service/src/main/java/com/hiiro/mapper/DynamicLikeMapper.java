package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.DynamicLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 动态点赞表 Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2026-08-23
 */
@Mapper
public interface DynamicLikeMapper extends BaseMapper<DynamicLike> {
}
