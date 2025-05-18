package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.Comment;
import org.apache.ibatis.annotations.Mapper;


/**
 * <p>
 * 评论表 Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2025-03-13
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

}

