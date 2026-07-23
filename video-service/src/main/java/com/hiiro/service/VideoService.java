package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 视频表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
public interface VideoService extends IService<Video> {

    /**
     * 获取推荐视频
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    ResultData<List<Map<String, Object>>> getRecommendVideos(Integer pageNum, Integer pageSize);

    /**
     * 获取全部视频
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    ResultData<List<Map<String, Object>>> getAllVideos(Integer pageNum, Integer pageSize);

    /**
     * 保存视频
     *
     * @param uid   用户id
     * @param video 视频对象
     * @return 保存视频是否成功
     */
    boolean saveVideo(String uid, Video video);

    /**
     * 根据视频id获取视频
     *
     * @param vid 视频id
     * @return ResultData对象
     */
    ResultData<HashMap<String, Object>> getVideoById(Long vid);

    /**
     * 修改视频信息
     *
     * @param video     视频对象
     * @param coverFile 封面文件
     * @return ResultData对象
     */
    ResultData<Video> updateVideo(Video video, MultipartFile coverFile);

    /**
     * 逻辑删除视频
     *
     * @param vid    视频id
     * @param status 状态
     * @return ResultData对象
     */
    ResultData<Video> updateVideoStatus(Long vid, Byte status);

    /**
     * 搜索视频
     *
     * @param keyword   关键字
     * @param pageNum   分页页数
     * @param pageSize  分页大小
     * @param order     排序方式：default-综合排序（相关度）、view-最多播放、date-最新发布、danmaku-最多弹幕、favorite-最多收藏
     * @return ResultData对象
     */
    ResultData<List<Map<String, Object>>> searchVideos(String keyword, Integer pageNum, Integer pageSize, String order);

    /**
     * 获取热搜列表
     *
     * @param limit 返回数量
     * @return ResultData对象
     */
    ResultData<List<String>> getHotSearchList(int limit);

    /**
     * 搜索建议
     *
     * @param keyword 输入的关键词前缀
     * @param limit   返回数量
     * @return 建议关键词列表
     */
    ResultData<List<String>> searchSuggest(String keyword, int limit);

    /**
     * 按用户ID获取投稿视频
     *
     * @param uid      用户ID
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    ResultData<List<Map<String, Object>>> getVideosByUid(Long uid, Integer pageNum, Integer pageSize);

    /**
     * 获取用户置顶视频
     *
     * @param uid 用户ID
     * @return 置顶视频详情（video + stat + category + user），无置顶返回null
     */
    ResultData<Map<String, Object>> getPinnedVideo(Long uid);

    /**
     * 设置置顶视频
     * 自动取消该用户原来的置顶视频
     *
     * @param uid 当前登录用户ID
     * @param vid 要置顶的视频ID
     * @return 是否成功
     */
    ResultData<String> setPinnedVideo(Long uid, Long vid);

    /**
     * 取消置顶视频
     *
     * @param uid 当前登录用户ID
     * @param vid 要取消置顶的视频ID
     * @return 是否成功
     */
    ResultData<String> cancelPinnedVideo(Long uid, Long vid);
}
