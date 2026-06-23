package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.VideoLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频点赞记录 Mapper 接口
 *
 * @author hiiro
 * @since 2025-06-23
 */
@Mapper
public interface VideoLikeMapper extends BaseMapper<VideoLike> {

}