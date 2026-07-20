package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.CommentDislike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论点踩记录 Mapper 接口
 *
 * @author hiiro
 * @since 2025-06-26
 */
@Mapper
public interface CommentDislikeMapper extends BaseMapper<CommentDislike> {

}
