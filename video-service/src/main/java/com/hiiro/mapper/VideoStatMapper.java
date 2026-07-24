package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.VideoStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;


/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2025-03-09
 */
@Mapper
public interface VideoStatMapper extends BaseMapper<VideoStat> {

    /**
     * 查询用户的视频统计数据汇总
     *
     * @param uid 用户ID
     * @return 统计数据 {totalVideos, totalViews, totalLikes, totalCoins, totalFavorites, totalDanmaku}
     */
    @Select("SELECT " +
            "  COUNT(v.vid) AS totalVideos, " +
            "  COALESCE(SUM(vs.view), 0) AS totalViews, " +
            "  COALESCE(SUM(vs.`like`), 0) AS totalLikes, " +
            "  COALESCE(SUM(vs.coin), 0) AS totalCoins, " +
            "  COALESCE(SUM(vs.favorite), 0) AS totalFavorites, " +
            "  COALESCE(SUM(vs.danmaku), 0) AS totalDanmaku " +
            "FROM video v " +
            "LEFT JOIN video_stat vs ON v.vid = vs.vid " +
            "WHERE v.uid = #{uid} AND v.status = 1")
    Map<String, Object> selectUserVideoStats(@Param("uid") Long uid);

}

