package com.hiiro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiiro.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


/**
 * <p>
 * 视频表 Mapper 接口
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 按用户ID分页查询投稿视频，支持按播放量/收藏量排序（需 join video_stat），并支持标题/简介关键字过滤
     *
     * @param uid      用户ID
     * @param offset   偏移量
     * @param pageSize 每页大小
     * @param order    排序字段：view / favorite
     * @param keyword  关键字（为空则不过滤）
     * @return 视频列表
     */
    @Select("SELECT v.* FROM video v " +
            "LEFT JOIN video_stat vs ON v.vid = vs.vid " +
            "WHERE v.uid = #{uid} AND v.status = 1 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR v.title LIKE CONCAT('%', #{keyword}, '%') OR v.descr LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY vs.${order} DESC, v.create_time DESC " +
            "LIMIT #{offset}, #{pageSize}")
    List<Video> selectUserVideosWithStatOrder(@Param("uid") Long uid,
                                              @Param("offset") long offset,
                                              @Param("pageSize") int pageSize,
                                              @Param("order") String order,
                                              @Param("keyword") String keyword);

    /**
     * 按用户ID查询投稿视频总数（用于分页计算），支持关键字过滤
     *
     * @param uid     用户ID
     * @param keyword 关键字（为空则不过滤）
     * @return 总数
     */
    @Select("SELECT COUNT(*) FROM video WHERE uid = #{uid} AND status = 1 " +
            "AND (#{keyword} IS NULL OR #{keyword} = '' OR title LIKE CONCAT('%', #{keyword}, '%') OR descr LIKE CONCAT('%', #{keyword}, '%'))")
    long countUserVideos(@Param("uid") Long uid, @Param("keyword") String keyword);

    /**
     * 热度分增量候选：近期有互动 或 仍在衰减窗口内的新建视频
     *
     * @param windowMinutes 近 N 分钟内有互动
     * @param recentDays    近 N 天内新建（仍在衰减窗口）
     * @return 视频列表（含 vid/uid/create_time/hot_score）
     */
    @Select("SELECT v.vid, v.uid, v.create_time, v.hot_score " +
            "FROM video v JOIN video_stat vs ON v.vid = vs.vid " +
            "WHERE v.status = 1 " +
            "  AND (vs.update_time > DATE_SUB(NOW(), INTERVAL #{windowMinutes} MINUTE) " +
            "       OR v.create_time > DATE_SUB(NOW(), INTERVAL #{recentDays} DAY))")
    List<Video> selectHotScoreCandidates(@Param("windowMinutes") int windowMinutes,
                                         @Param("recentDays") int recentDays);

}
