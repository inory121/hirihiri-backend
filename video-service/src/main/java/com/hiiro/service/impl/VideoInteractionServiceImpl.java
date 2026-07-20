package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.FavoriteFolderMapper;
import com.hiiro.mapper.VideoCoinMapper;
import com.hiiro.mapper.VideoCollectMapper;
import com.hiiro.mapper.VideoDislikeMapper;
import com.hiiro.mapper.VideoLikeMapper;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.CategoryService;
import com.hiiro.service.VideoInteractionService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 视频互动服务实现类
 *
 * @author hiiro
 * @since 2025-06-23
 */
@Service
public class VideoInteractionServiceImpl implements VideoInteractionService {

    @Resource
    private VideoLikeMapper videoLikeMapper;

    @Resource
    private VideoDislikeMapper videoDislikeMapper;

    @Resource
    private VideoCoinMapper videoCoinMapper;

    @Resource
    private VideoCollectMapper videoCollectMapper;

    @Resource
    private FavoriteFolderMapper favoriteFolderMapper;

    @Resource
    private VideoStatService videoStatService;

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private CategoryService categoryService;

    @Resource
    private UserFeignApi userFeignApi;

    @Override
    @Transactional
    public ResultData<String> toggleLike(Long uid, Long vid) {
        VideoLike existing = videoLikeMapper.selectOne(
                new LambdaQueryWrapper<VideoLike>()
                        .eq(VideoLike::getUid, uid)
                        .eq(VideoLike::getVid, vid)
        );

        if (existing != null) {
            videoLikeMapper.delete(
                    new LambdaQueryWrapper<VideoLike>()
                            .eq(VideoLike::getUid, uid)
                            .eq(VideoLike::getVid, vid)
            );
            videoStatService.decrementLike(vid);
            return ResultData.success("取消点赞");
        } else {
            VideoLike videoLike = new VideoLike();
            videoLike.setUid(uid);
            videoLike.setVid(vid);
            videoLike.setCreateTime(LocalDateTime.now());
            videoLikeMapper.insert(videoLike);
            videoStatService.incrementLike(vid);

            // 点赞时取消点踩
            VideoDislike disliked = videoDislikeMapper.selectOne(
                    new LambdaQueryWrapper<VideoDislike>()
                            .eq(VideoDislike::getUid, uid)
                            .eq(VideoDislike::getVid, vid)
            );
            if (disliked != null) {
                videoDislikeMapper.delete(
                        new LambdaQueryWrapper<VideoDislike>()
                                .eq(VideoDislike::getUid, uid)
                                .eq(VideoDislike::getVid, vid)
                );
                videoStatService.decrementDislike(vid);
            }

            return ResultData.success("点赞成功");
        }
    }

    @Override
    @Transactional
    public ResultData<String> toggleDislike(Long uid, Long vid) {
        VideoDislike existing = videoDislikeMapper.selectOne(
                new LambdaQueryWrapper<VideoDislike>()
                        .eq(VideoDislike::getUid, uid)
                        .eq(VideoDislike::getVid, vid)
        );

        if (existing != null) {
            videoDislikeMapper.delete(
                    new LambdaQueryWrapper<VideoDislike>()
                            .eq(VideoDislike::getUid, uid)
                            .eq(VideoDislike::getVid, vid)
            );
            videoStatService.decrementDislike(vid);
            return ResultData.success("取消点踩");
        } else {
            VideoDislike videoDislike = new VideoDislike();
            videoDislike.setUid(uid);
            videoDislike.setVid(vid);
            videoDislike.setCreateTime(LocalDateTime.now());
            videoDislikeMapper.insert(videoDislike);
            videoStatService.incrementDislike(vid);

            // 点踩时取消点赞
            VideoLike liked = videoLikeMapper.selectOne(
                    new LambdaQueryWrapper<VideoLike>()
                            .eq(VideoLike::getUid, uid)
                            .eq(VideoLike::getVid, vid)
            );
            if (liked != null) {
                videoLikeMapper.delete(
                        new LambdaQueryWrapper<VideoLike>()
                                .eq(VideoLike::getUid, uid)
                                .eq(VideoLike::getVid, vid)
                );
                videoStatService.decrementLike(vid);
            }

            return ResultData.success("点踩成功");
        }
    }

    @Override
    @Transactional
    public ResultData<String> toggleCoin(Long uid, Long vid) {
        VideoCoin existing = videoCoinMapper.selectOne(
                new LambdaQueryWrapper<VideoCoin>()
                        .eq(VideoCoin::getUid, uid)
                        .eq(VideoCoin::getVid, vid)
        );

        if (existing != null) {
            videoCoinMapper.delete(
                    new LambdaQueryWrapper<VideoCoin>()
                            .eq(VideoCoin::getUid, uid)
                            .eq(VideoCoin::getVid, vid)
            );
            videoStatService.decrementCoin(vid);
            return ResultData.success("取消投币");
        } else {
            VideoCoin videoCoin = new VideoCoin();
            videoCoin.setUid(uid);
            videoCoin.setVid(vid);
            videoCoin.setCreateTime(LocalDateTime.now());
            videoCoinMapper.insert(videoCoin);
            videoStatService.incrementCoin(vid);
            return ResultData.success("投币成功");
        }
    }

    @Override
    @Transactional
    public ResultData<String> collectToFolder(Long uid, Long vid, Long folderId) {
        // 验证收藏夹是否存在且属于该用户
        FavoriteFolder folder = favoriteFolderMapper.selectOne(
                new LambdaQueryWrapper<FavoriteFolder>()
                        .eq(FavoriteFolder::getId, folderId)
                        .eq(FavoriteFolder::getUid, uid)
        );

        if (folder == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "收藏夹不存在或无权操作");
        }

        // 检查是否已经收藏到该收藏夹（主键是 uid+vid+folder_id）
        VideoCollect existing = videoCollectMapper.selectOne(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .eq(VideoCollect::getVid, vid)
                        .eq(VideoCollect::getFolderId, folderId)
        );

        if (existing != null) {
            // 已在该收藏夹，取消收藏
            videoCollectMapper.delete(
                    new LambdaQueryWrapper<VideoCollect>()
                            .eq(VideoCollect::getUid, uid)
                            .eq(VideoCollect::getVid, vid)
                            .eq(VideoCollect::getFolderId, folderId)
            );
            
            // 更新收藏夹视频数量
            folder.setVideoCount(Math.max(0, folder.getVideoCount() - 1));
            folder.setUpdateTime(LocalDateTime.now());
            favoriteFolderMapper.updateById(folder);
            
            // 检查该视频是否还在其他收藏夹
            long remainingCount = videoCollectMapper.selectCount(
                    new LambdaQueryWrapper<VideoCollect>()
                            .eq(VideoCollect::getUid, uid)
                            .eq(VideoCollect::getVid, vid)
            );
            
            // 只有所有收藏夹都不再收藏时才减少视频收藏数
            if (remainingCount == 0) {
                videoStatService.decrementFavorite(vid);
            }
            
            return ResultData.success("取消收藏");
        } else {
            // 检查该视频是否已被该用户收藏到其他收藏夹
            long existingCollectCount = videoCollectMapper.selectCount(
                    new LambdaQueryWrapper<VideoCollect>()
                            .eq(VideoCollect::getUid, uid)
                            .eq(VideoCollect::getVid, vid)
            );

            // 新收藏
            VideoCollect videoCollect = new VideoCollect();
            videoCollect.setUid(uid);
            videoCollect.setVid(vid);
            videoCollect.setFolderId(folderId);
            videoCollect.setCreateTime(LocalDateTime.now());
            videoCollectMapper.insert(videoCollect);

            // 更新收藏夹视频数量
            folder.setVideoCount(folder.getVideoCount() + 1);
            folder.setUpdateTime(LocalDateTime.now());
            favoriteFolderMapper.updateById(folder);

            // 只有当用户是第一次收藏该视频时才增加视频收藏数
            if (existingCollectCount == 0) {
                videoStatService.incrementFavorite(vid);
            }
            return ResultData.success("收藏成功");
        }
    }

    @Override
    public ResultData<Boolean[]> getInteractionStatus(Long uid, Long vid) {
        boolean liked = videoLikeMapper.selectOne(
                new LambdaQueryWrapper<VideoLike>()
                        .eq(VideoLike::getUid, uid)
                        .eq(VideoLike::getVid, vid)
        ) != null;

        boolean disliked = videoDislikeMapper.selectOne(
                new LambdaQueryWrapper<VideoDislike>()
                        .eq(VideoDislike::getUid, uid)
                        .eq(VideoDislike::getVid, vid)
        ) != null;

        boolean coined = videoCoinMapper.selectOne(
                new LambdaQueryWrapper<VideoCoin>()
                        .eq(VideoCoin::getUid, uid)
                        .eq(VideoCoin::getVid, vid)
        ) != null;

        // 检查视频是否在任意收藏夹中
        boolean favorited = videoCollectMapper.selectCount(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .eq(VideoCollect::getVid, vid)
        ) > 0;

        return ResultData.success(new Boolean[]{liked, disliked, coined, favorited});
    }

    /**
     * 根据视频ID列表构建完整的视频信息Map列表
     */
    private List<Map<String, Object>> buildVideoInfoList(List<Long> vidList) {
        if (vidList == null || vidList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Video> videos = videoMapper.selectBatchIds(vidList);
        if (videos.isEmpty()) {
            return Collections.emptyList();
        }

        // 保持原始顺序
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getVid, v -> v, (a, b) -> a));
        List<Video> orderedVideos = new ArrayList<>();
        for (Long vid : vidList) {
            Video v = videoMap.get(vid);
            if (v != null) {
                orderedVideos.add(v);
            }
        }

        // 批量获取用户信息
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

            VideoStat stat = videoStatService.getVideoStatByVid(video.getVid());
            if (stat == null) {
                stat = new VideoStat();
                stat.setVid(video.getVid());
            }
            map.put("stat", stat);

            Category category = categoryService.getCategoryById(video.getMcId(), video.getScId());
            if (category == null) {
                category = new Category();
            }
            map.put("category", category);

            UserDTO user = userMap.getOrDefault(video.getUid(), new UserDTO());
            map.put("user", user);

            result.add(map);
        }
        return result;
    }

    @Override
    public ResultData<List<Map<String, Object>>> getRecentCoinVideos(Long uid, Integer limit) {
        if (limit == null || limit < 1) limit = 10;

        List<VideoCoin> coins = videoCoinMapper.selectList(
                new LambdaQueryWrapper<VideoCoin>()
                        .eq(VideoCoin::getUid, uid)
                        .orderByDesc(VideoCoin::getCreateTime)
                        .last("LIMIT " + limit)
        );

        List<Long> vidList = coins.stream().map(VideoCoin::getVid).toList();
        List<Map<String, Object>> videoInfos = buildVideoInfoList(vidList);
        return ResultData.success(videoInfos);
    }

    @Override
    public ResultData<List<Map<String, Object>>> getRecentLikeVideos(Long uid, Integer limit) {
        if (limit == null || limit < 1) limit = 10;

        List<VideoLike> likes = videoLikeMapper.selectList(
                new LambdaQueryWrapper<VideoLike>()
                        .eq(VideoLike::getUid, uid)
                        .orderByDesc(VideoLike::getCreateTime)
                        .last("LIMIT " + limit)
        );

        List<Long> vidList = likes.stream().map(VideoLike::getVid).toList();
        List<Map<String, Object>> videoInfos = buildVideoInfoList(vidList);
        return ResultData.success(videoInfos);
    }
}
