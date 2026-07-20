package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;


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

    @Select("<script>" +
            "SELECT following_uid AS uid, COUNT(*) AS fan_count FROM follow " +
            "WHERE following_uid IN " +
            "<foreach item='uid' collection='uids' open='(' separator=',' close=')'>#{uid}</foreach>" +
            " GROUP BY following_uid" +
            "</script>")
    List<Map<String, Object>> getFanCountBatch(@Param("uids") List<Long> uids);

    @Select("<script>" +
            "SELECT uid, COUNT(*) AS video_count FROM video " +
            "WHERE uid IN " +
            "<foreach item='uid' collection='uids' open='(' separator=',' close=')'>#{uid}</foreach>" +
            " AND status = 1 GROUP BY uid" +
            "</script>")
    List<Map<String, Object>> getVideoCountBatch(@Param("uids") List<Long> uids);
}