package com.hiiro.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.MessageNoticeCreateDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.*;
import com.hiiro.service.CategoryService;
import com.hiiro.service.VideoInteractionService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            // 取消点赞同步删除对应的点赞通知（失败不影响取消点赞主流程）
            try {
                Video video = videoMapper.selectById(vid);
                Long authorUid = video != null ? video.getUid() : null;
                if (authorUid != null && !Objects.equals(authorUid, uid)) {
                    userFeignApi.deleteInternalNotice(authorUid, uid, "like", "video", vid);
                }
            } catch (Exception e) {
                log.warn("点赞通知删除失败: {}", e.getMessage());
            }
            return ResultData.success("取消点赞");
        } else {
            VideoLike videoLike = new VideoLike();
            videoLike.setUid(uid);
            videoLike.setVid(vid);
            videoLike.setCreateTime(LocalDateTime.now());
            videoLikeMapper.insert(videoLike);
            videoStatService.incrementLike(vid);

            // 生成点赞通知（失败不影响点赞主流程）
            try {
                Video video = videoMapper.selectById(vid);
                Long authorUid = video != null ? video.getUid() : null;
                if (authorUid != null && !Objects.equals(authorUid, uid)) {
                    MessageNoticeCreateDTO noticeDTO = new MessageNoticeCreateDTO();
                    noticeDTO.setReceiveUid(authorUid);
                    noticeDTO.setActorUid(uid);
                    noticeDTO.setNoticeType("like");
                    noticeDTO.setBizType("video");
                    noticeDTO.setBizId(vid);
                    noticeDTO.setContentSummary(null);
                    noticeDTO.setExtJson(buildVideoExtJson(vid));
                    userFeignApi.createInternalNotice(noticeDTO);
                }
            } catch (Exception e) {
                log.warn("点赞通知生成失败: {}", e.getMessage());
            }

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

    /**
     * 构建点赞通知扩展 JSON：塞入视频ID（用于跳转）与封面/标题（用于列表直接展示，避免前端二次查视频）
     */
    private String buildVideoExtJson(Long vid) {
        if (vid == null) return null;
        Video video = videoMapper.selectById(vid);
        if (video == null) return null;
        JSONObject ext = new JSONObject();
        ext.put("videoId", vid);
        if (video.getCoverUrl() != null) ext.put("videoCover", video.getCoverUrl());
        if (video.getTitle() != null) ext.put("videoTitle", video.getTitle());
        return ext.toJSONString();
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
    public ResultData<String> toggleCoin(Long uid, Long vid, Integer count) {
        // 每次投币固定 1 币（B站规则：单个用户对单个视频最多投 2 币，可分次投）
        if (count == null || count != 1) {
            count = 1;
        }

        Video video = videoMapper.selectById(vid);
        if (video == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "视频不存在");
        }
        Long authorUid = video.getUid();

        // 自己不能给自己投币
        if (Objects.equals(authorUid, uid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "UP主不能给自己投币");
        }

        VideoCoin existing = videoCoinMapper.selectOne(
                new LambdaQueryWrapper<VideoCoin>()
                        .eq(VideoCoin::getUid, uid)
                        .eq(VideoCoin::getVid, vid)
        );
        int alreadyCoined = existing == null || existing.getCount() == null ? 0 : existing.getCount();

        // 已投满 2 币（单个用户对单个视频最多 2 币）
        if (alreadyCoined >= 2) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "对本稿件的投币枚数已用完");
        }

        // 检查观众硬币余额
        ResultData<UserDTO> userResult = userFeignApi.getUserByUid(uid);
        if (userResult == null || userResult.getData() == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "获取用户信息失败");
        }
        UserDTO userDTO = userResult.getData();
        if (userDTO.getCoin() == null || userDTO.getCoin() < count) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "硬币不足");
        }

        // 投币：累计记录、增加视频统计、扣除观众硬币、UP主获得10%奖励、观众获得投币经验
        if (existing == null) {
            VideoCoin videoCoin = new VideoCoin();
            videoCoin.setUid(uid);
            videoCoin.setVid(vid);
            videoCoin.setCount(count);
            videoCoin.setCreateTime(LocalDateTime.now());
            videoCoinMapper.insert(videoCoin);
        } else {
            // 已投过 1 币，再投 1 币则累加（video_coin 为 (uid,vid) 复合主键，updateById 不可用，改用条件更新）
            videoCoinMapper.update(null,
                    new LambdaUpdateWrapper<VideoCoin>()
                            .eq(VideoCoin::getUid, uid)
                            .eq(VideoCoin::getVid, vid)
                            .set(VideoCoin::getCount, alreadyCoined + count));
        }
        videoStatService.incrementCoin(vid, count);
        ResultData<String> viewerCoinResult = userFeignApi.addCoin(uid, -count.doubleValue());
        ResultData<String> authorCoinResult = userFeignApi.addCoin(authorUid, count * 0.1);
        log.info("[投币] uid={} 扣币{} -> code={} msg={}; authorUid={} 加币{} -> code={} msg={}",
                uid, -count, viewerCoinResult == null ? "null" : viewerCoinResult.getCode(),
                viewerCoinResult == null ? "null" : viewerCoinResult.getMessage(),
                authorUid, count * 0.1,
                authorCoinResult == null ? "null" : authorCoinResult.getCode(),
                authorCoinResult == null ? "null" : authorCoinResult.getMessage());
        // 投币经验：1币=10经验，每日上限50
        userFeignApi.addCoinExp(uid, count * 10);
        return ResultData.success("投币成功");
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
    public ResultData<Object[]> getInteractionStatus(Long uid, Long vid) {
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

        // 已投币数（0/1/2），用于前端判断剩余可投数量
        VideoCoin coinRecord = videoCoinMapper.selectOne(
                new LambdaQueryWrapper<VideoCoin>()
                        .eq(VideoCoin::getUid, uid)
                        .eq(VideoCoin::getVid, vid)
        );
        int coinCount = coinRecord == null || coinRecord.getCount() == null ? 0 : coinRecord.getCount();

        // 检查视频是否在任意收藏夹中
        boolean favorited = videoCollectMapper.selectCount(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .eq(VideoCollect::getVid, vid)
        ) > 0;

        // 数组：liked, disliked, coinCount(已投币数), favorited
        return ResultData.success(new Object[]{liked, disliked, coinCount, favorited});
    }

    /**
     * 根据视频ID列表构建完整的视频信息Map列表
     */
    private List<Map<String, Object>> buildVideoInfoList(List<Long> vidList) {
        if (vidList == null || vidList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Video> videos = videoMapper.selectByIds(vidList);
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
