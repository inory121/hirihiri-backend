package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.FavoriteFolder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收藏夹 Mapper 接口
 *
 * @author hiiro
 * @since 2025-06-23
 */
@Mapper
public interface FavoriteFolderMapper extends BaseMapper<FavoriteFolder> {

}
