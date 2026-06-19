package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.UserBrowseHistory;
import com.hiiro.entity.dto.HistoryVideoDTO;

import java.util.List;

/**
 * <p>
 * 用户浏览历史服务接口
 * </p>
 *
 * @author hiiro
 * @since 2025-06-15
 */
public interface UserBrowseHistoryService extends IService<UserBrowseHistory> {

    /**
     * 保存或更新浏览历史
     *
     * @param uid      用户ID
     * @param vid      视频ID
     * @param progress 播放进度(秒)
     * @return 保存结果
     */
    int saveOrUpdateHistory(Integer uid, Integer vid, Integer progress);

    /**
     * 分页获取用户浏览历史列表(包含视频信息)
     *
     * @param uid      用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 历史记录列表
     */
    List<HistoryVideoDTO> getHistoryPageList(Integer uid, Integer pageNum, Integer pageSize);

    /**
     * 获取用户指定视频的浏览进度
     *
     * @param uid 用户ID
     * @param vid 视频ID
     * @return 浏览历史(无则返回null)
     */
    UserBrowseHistory getHistoryByUidAndVid(Integer uid, Integer vid);
}