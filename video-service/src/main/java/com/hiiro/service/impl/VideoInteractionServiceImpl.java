package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.VideoCoin;
import com.hiiro.entity.VideoCollect;
import com.hiiro.entity.VideoLike;
import com.hiiro.mapper.VideoCoinMapper;
import com.hiiro.mapper.VideoCollectMapper;
import com.hiiro.mapper.VideoLikeMapper;
import com.hiiro.service.VideoInteractionService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    private VideoCoinMapper videoCoinMapper;

    @Resource
    private VideoCollectMapper videoCollectMapper;

    @Resource
    private VideoStatService videoStatService;

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
            return ResultData.success("点赞成功");
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
    public ResultData<String> toggleCollect(Long uid, Long vid) {
        VideoCollect existing = videoCollectMapper.selectOne(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .eq(VideoCollect::getVid, vid)
        );

        if (existing != null) {
            videoCollectMapper.delete(
                    new LambdaQueryWrapper<VideoCollect>()
                            .eq(VideoCollect::getUid, uid)
                            .eq(VideoCollect::getVid, vid)
            );
            videoStatService.decrementFavorite(vid);
            return ResultData.success("取消收藏");
        } else {
            VideoCollect videoCollect = new VideoCollect();
            videoCollect.setUid(uid);
            videoCollect.setVid(vid);
            videoCollect.setCreateTime(LocalDateTime.now());
            videoCollectMapper.insert(videoCollect);
            videoStatService.incrementFavorite(vid);
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

        boolean coined = videoCoinMapper.selectOne(
                new LambdaQueryWrapper<VideoCoin>()
                        .eq(VideoCoin::getUid, uid)
                        .eq(VideoCoin::getVid, vid)
        ) != null;

        boolean favorited = videoCollectMapper.selectOne(
                new LambdaQueryWrapper<VideoCollect>()
                        .eq(VideoCollect::getUid, uid)
                        .eq(VideoCollect::getVid, vid)
        ) != null;

        return ResultData.success(new Boolean[]{liked, coined, favorited});
    }
}