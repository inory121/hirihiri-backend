package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.RecommendEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 推荐行为事件 Mapper 接口
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Mapper
public interface RecommendEventMapper extends BaseMapper<RecommendEvent> {

}