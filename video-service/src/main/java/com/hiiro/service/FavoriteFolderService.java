package com.hiiro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hiiro.entity.FavoriteFolder;
import com.hiiro.entity.ResultData;

import java.util.List;
import java.util.Map;

/**
 * 收藏夹服务
 *
 * @author hiiro
 * @since 2025-06-23
 */
public interface FavoriteFolderService {

    /**
     * 获取用户的收藏夹列表
     *
     * @param uid 用户ID
     * @param vid 视频ID（可选，用于查询视频在哪些收藏夹中）
     * @param isOwner 是否是用户本人查询（本人查询时会自动修复/创建默认收藏夹）
     * @return 收藏夹列表
     */
    ResultData<List<FavoriteFolder>> getUserFolders(Long uid, Long vid, boolean isOwner);

    /**
     * 创建新收藏夹
     *
     * @param uid 用户ID
     * @param folderName 收藏夹名称
     * @param description 描述（可选）
     * @return 创建的收藏夹
     */
    ResultData<FavoriteFolder> createFolder(Long uid, String folderName, String description);

    /**
     * 更新收藏夹信息
     *
     * @param uid 用户ID
     * @param folderId 收藏夹ID
     * @param folderName 新的收藏夹名称
     * @param description 新的描述
     * @return 更新结果
     */
    ResultData<String> updateFolder(Long uid, Long folderId, String folderName, String description);

    /**
     * 删除收藏夹
     *
     * @param uid 用户ID
     * @param folderId 收藏夹ID
     * @return 删除结果
     */
    ResultData<String> deleteFolder(Long uid, Long folderId);

    /**
     * 获取收藏夹中的视频列表（分页）
     *
     * @param uid 当前登录用户ID（可选，用于判断是否是所有者）
     * @param folderId 收藏夹ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 视频列表
     */
    ResultData<Page<Map<String, Object>>> getFolderVideos(Long uid, Long folderId, Integer pageNum, Integer pageSize);

    /**
     * 获取用户最近收藏的视频列表（不分收藏夹）
     *
     * @param uid 用户ID
     * @param limit 限制数量
     * @return 视频列表
     */
    ResultData<List<Map<String, Object>>> getRecentFavorites(Long uid, Integer limit);
}
