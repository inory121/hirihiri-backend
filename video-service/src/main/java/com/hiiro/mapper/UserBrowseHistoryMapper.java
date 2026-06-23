package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.UserBrowseHistory;
import com.hiiro.entity.dto.HistoryVideoDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 用户浏览历史 Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2025-06-15
 */
@Mapper
public interface UserBrowseHistoryMapper extends BaseMapper<UserBrowseHistory> {

    /**
     * 分页查询浏览历史(关联视频和用户表)
     */
    @Select("SELECT " +
            "h.id, h.vid, h.browse_time, h.progress, " +
            "v.title, v.cover_url, v.duration, v.uid as author_uid, " +
            "u.username as author_username " +
            "FROM user_browse_history h " +
            "LEFT JOIN video v ON h.vid = v.vid " +
            "LEFT JOIN user u ON v.uid = u.uid " +
            "WHERE h.uid = #{uid} AND v.status = 1 " +
            "ORDER BY h.browse_time DESC " +
            "LIMIT #{offset}, #{pageSize}")
    List<HistoryVideoDTO> selectHistoryWithVideo(@Param("uid") Long uid, 
                                                  @Param("offset") Integer offset, 
                                                  @Param("pageSize") Integer pageSize);
}