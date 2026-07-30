package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.FavoriteFolderMapper;
import com.hiiro.mapper.VideoCollectMapper;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.CategoryService;
import com.hiiro.service.FavoriteFolderService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 收藏夹服务实现类
 *
 * @author hiiro
 * @since 2025-06-23
 */
@Service
public class FavoriteFolderServiceImpl implements FavoriteFolderService {

    @Resource
    private FavoriteFolderMapper favoriteFolderMapper;

    @Resource
    private VideoCollectMapper videoCollectMapper;

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private VideoStatService videoStatService;

    @Resource
    private CategoryService categoryService;

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 根据视频ID列表构建完整的视频信息Map列表
     */
    private List<Map<String, Object>> buildVideoInfoList(List<Long> vidList) {
        if (vidList == null || vidList.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询视频
        List<Video> videos = videoMapper.selectByIds(vidList);
        if (videos.isEmpty()) {
            return Collections.emptyList();
        }

        // 保持顺序
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getVid, v -> v, (a, b) -> a));
        List<Video> orderedVideos = new ArrayList<>();
        for (Long vid : vidList) {
            Video v = videoMap.get(vid);
            if (v != null) {
                orderedVideos.add(v);
            }
        }

        // 批量获取统计、分类、用户信息
        List<Long> uids = orderedVideos.stream().map(Video::getUid).distinct().toList();
        Map<Long, UserDTO> userMap = new HashMap<>();
        try {
            List<UserDTO> users = userFeignApi.getBatchUserInfo(uids);
            for (UserDTO u : users) {
                userMap.put(u.getUid(), u);
            }
        } catch (Exception ignored) {
        }

        List<Map<String, Object>> result = new ArrayList<>(orderedVideos.size());
        for (Video video : orderedVideos) {
            Map<String, Object> map = new HashMap<>(8);
            map.put("video", video);

            // 统计
            VideoStat stat = videoStatService.getVideoStatByVid(video.getVid());
            if (stat == null) {
                stat = new VideoStat();
                stat.setVid(video.getVid());
            }
            map.put("stat", stat);

            // 分类
            Category category = categoryService.getCategoryById(video.getMcId(), video.getScId());
            if (category == null) {
                category = new Category();
            }
            map.put("category", category);

            // 用户
            UserDTO user = userMap.getOrDefault(video.getUid(), new UserDTO());
            map.put("user", user);

            result.add(map);
        }
        return result;
    }

    @Override
    public ResultData<List<FavoriteFolder>> getUserFolders(Long uid, Long vid, boolean isOwner) {
        List<FavoriteFolder> folders = favoriteFolderMapper.selectList(
                new LambdaQueryWrapper<FavoriteFolder>()
                        .eq(FavoriteFolder::getUid, uid)
                        .orderByDesc(FavoriteFolder::getIsDefault)
                        .orderByDesc(FavoriteFolder::getUpdateTime)
        );

        // 只有用户本人查询自己收藏夹时，才自动修复/创建默认收藏夹
        if (isOwner) {
            // 如果没有默认收藏夹，创建一个或修复
            boolean hasDefault = folders.stream().anyMatch(f -> Boolean.TRUE.equals(f.getIsDefault()));
            if (!hasDefault && !folders.isEmpty()) {
                FavoriteFolder defaultFolder = folders.get(0);
                defaultFolder.setIsDefault(true);
                favoriteFolderMapper.updateById(defaultFolder);
            } else if (folders.isEmpty()) {
                // 自动创建默认收藏夹
                FavoriteFolder defaultFolder = createFolder(uid, "默认收藏夹", null).getData();
                if (defaultFolder != null) {
                    defaultFolder.setIsDefault(true);
                    favoriteFolderMapper.updateById(defaultFolder);
                    folders.add(defaultFolder);
                }
            }
        }

        // 用 video_collect 表的实际记录数校正 videoCount，并获取第一个视频的封面
        for (FavoriteFolder folder : folders) {
            Long realCount = videoCollectMapper.selectCount(
                    new LambdaQueryWrapper<VideoCollect>()
                            .eq(VideoCollect::getUid, uid)
                            .eq(VideoCollect::getFolderId, folder.getId())
            );
            folder.setVideoCount(realCount.intValue());

            // 获取该收藏夹中第一个视频的封面作为收藏夹封面
            if (realCount > 0) {
                VideoCollect firstCollect = videoCollectMapper.selectOne(
                        new LambdaQueryWrapper<VideoCollect>()
                                .eq(VideoCollect::getUid, uid)
                                .eq(VideoCollect::getFolderId, folder.getId())
                                .orderByDesc(VideoCollect::getCreateTime)
                                .last("LIMIT 1")
                );
                if (firstCollect != null) {
                    Video video = videoMapper.selectById(firstCollect.getVid());
                    if (video != null) {
                        folder.setCoverUrl(video.getCoverUrl());
                    }
                }
            } else {
                folder.setCoverUrl(null);
            }

            // 如果传入了 vid，检查该视频是否在此收藏夹中
            if (vid != null) {
                Long count = videoCollectMapper.selectCount(
                        new LambdaQueryWrapper<VideoCollect>()
                                .eq(VideoCollect::getUid, uid)
                                .eq(VideoCollect::getVid, vid)
                                .eq(VideoCollect::getFolderId, folder.getId())
                );
                folder.setCollected(count > 0);
            } else {
                folder.setCollected(false);
            }
        }

        return ResultData.success(folders);
    }

    @Override
    @Transactional
    public ResultData<FavoriteFolder> createFolder(Long uid, String folderName, String description) {
        // 检查是否已存在同名收藏夹
        Long count = favoriteFolderMapper.selectCount(
                new LambdaQueryWrapper<FavoriteFolder>()
                        .eq(FavoriteFolder::getUid, uid)
                        .eq(FavoriteFolder::getName, folderName)
        );

        if (count > 0) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "已存在同名收藏夹");
        }

        FavoriteFolder folder = new FavoriteFolder();
        folder.setUid(uid);
        folder.setName(folderName);
        folder.setDescription(description);
        folder.setVideoCount(0);
        folder.setIsDefault(false);
        folder.setCreateTime(LocalDateTime.now());
        folder.setUpdateTime(LocalDateTime.now());

        favoriteFolderMapper.insert(folder);

        return ResultData.success(folder);
    }

    @Override
    @Transactional
    public ResultData<String> updateFolder(Long uid, Long folderId, String folderName, String description) {
        FavoriteFolder folder = favoriteFolderMapper.selectOne(
                new LambdaQueryWrapper<FavoriteFolder>()
                        .eq(FavoriteFolder::getId, folderId)
                        .eq(FavoriteFolder::getUid, uid)
        );

        if (folder == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "收藏夹不存在或无权操作");
        }

        // 默认收藏夹不允许修改名称
        if (Boolean.TRUE.equals(folder.getIsDefault())) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "默认收藏夹不允许修改");
        }

        // 如果修改名称，检查是否重名
        if (folderName != null && !folderName.equals(folder.getName())) {
            Long count = favoriteFolderMapper.selectCount(
                    new LambdaQueryWrapper<FavoriteFolder>()
                            .eq(FavoriteFolder::getUid, uid)
                            .eq(FavoriteFolder::getName, folderName)
                            .ne(FavoriteFolder::getId, folderId)
            );

            if (count > 0) {
                return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "已存在同名收藏夹");
            }
            folder.setName(folderName);
        }

        if (description != null) {
            folder.setDescription(description);
        }

        folder.setUpdateTime(LocalDateTime.now());
        favoriteFolderMapper.updateById(folder);

        return ResultData.success("更新成功");
    }

    @Override
    @Transactional
    public ResultData<String> deleteFolder(Long uid, Long folderId) {
        FavoriteFolder folder = favoriteFolderMapper.selectOne(
                new LambdaQueryWrapper<FavoriteFolder>()
                        .eq(FavoriteFolder::getId, folderId)
                        .eq(FavoriteFolder::getUid, uid)
        );

        if (folder == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "收藏夹不存在或无权操作");
        }

        // 不允许删除默认收藏夹
        if (Boolean.TRUE.equals(folder.getIsDefault())) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "不能删除默认收藏夹");
        }

        // 获取该收藏夹中的所有收藏记录
        List<VideoCollect> collects = videoCollectMapper.selectList(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .eq(VideoCollect::getFolderId, folderId)
        );

        // 删除收藏夹中的所有收藏记录
        videoCollectMapper.delete(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .eq(VideoCollect::getFolderId, folderId)
        );

        // 减少统计数
        for (VideoCollect collect : collects) {
            videoStatService.decrementFavorite(collect.getVid());
        }

        // 删除收藏夹
        favoriteFolderMapper.deleteById(folderId);

        return ResultData.success("删除成功");
    }

    @Override
    public ResultData<Page<Map<String, Object>>> getFolderVideos(Long uid, Long folderId, Integer pageNum, Integer pageSize) {
        // 先根据folderId查询收藏夹，获取所有者uid（公开收藏夹任何人可查看）
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);

        if (folder == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "收藏夹不存在");
        }

        // 使用收藏夹所有者的uid查询视频
        Long ownerUid = folder.getUid();

        // 查询该收藏夹中的视频（分页）
        if (pageNum == null || pageNum < 1) pageNum = 1;
        if (pageSize == null || pageSize < 1) pageSize = 20;

        Page<VideoCollect> collectPage = new Page<>(pageNum, pageSize);
        Page<VideoCollect> result = videoCollectMapper.selectPage(
                collectPage,
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, ownerUid)
                        .eq(VideoCollect::getFolderId, folderId)
                        .orderByDesc(VideoCollect::getCreateTime)
        );

        // 获取视频ID列表
        List<Long> vidList = result.getRecords().stream()
                .map(VideoCollect::getVid)
                .toList();

        // 构建完整视频信息
        List<Map<String, Object>> videoInfos = buildVideoInfoList(vidList);

        // 构建返回结果
        Page<Map<String, Object>> videoPage = new Page<>(pageNum, pageSize, result.getTotal());
        videoPage.setRecords(videoInfos);

        return ResultData.success(videoPage);
    }

    @Override
    public ResultData<List<Map<String, Object>>> getRecentFavorites(Long uid, Integer limit) {
        if (limit == null || limit < 1) limit = 10;

        // 查询最近收藏的视频ID列表
        List<VideoCollect> collects = videoCollectMapper.selectList(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .orderByDesc(VideoCollect::getCreateTime)
                        .last("LIMIT " + limit)
        );

        List<Long> vidList = collects.stream()
                .map(VideoCollect::getVid)
                .toList();

        List<Map<String, Object>> videoInfos = buildVideoInfoList(vidList);

        return ResultData.success(videoInfos);
    }
}
