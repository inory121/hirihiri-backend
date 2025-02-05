package com.hiiro.mapper;

import com.hiiro.entity.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;


/**
 * <p>
 * 分区表 Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2025-01-28
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

}

