package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.CategoryService;
import com.hiiro.service.VideoService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * <p>
 * 视频表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
@Slf4j
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Resource
    VideoStatService videoStatService;
    @Resource
    private VideoMapper videoMapper;
    @Resource
    private CategoryService categoryService;
    @Resource(name = "videoAsyncExecutor")
    private Executor asyncExecutor;
    @Resource
    UserFeignApi userFeignApi;

    /**
     * 获取推荐视频
     *
     * @return 推荐视频列表
     */
    @Override
    public ResultData<List<Map<String, Object>>> getRecommendVideos(Integer pageNum, Integer pageSize) {
//        long start = System.currentTimeMillis();
        // 设置默认分页参数
        if (pageNum == null || pageNum < 1) {
            pageNum = 1; // 最小页数1
        } else if (pageNum > 100) {
            pageNum = 100; // 最大页数100
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10; // 默认每页10条
        } else if (pageSize > 50) {
            pageSize = 50; // 最大每页50条
        }
        Page<Video> page = new Page<>(pageNum, pageSize);
        IPage<Video> videoPage = new LambdaQueryChainWrapper<>(videoMapper)
                .ne(Video::getStatus, 3)
                .page(page);
        List<Video> videoList = videoPage.getRecords();
        if (videoList.isEmpty()) {
            return ResultData.success(Collections.emptyList(), "无推荐视频");
        }

        // 在获取视频列表后，提取所有用户ID
        List<Long> uids = videoList.stream()
                .map(Video::getUid) //
                .distinct()
                .toList();

        //收集批量查询参数
        List<Pair<String, String>> mcScIdPairs = videoList.stream()
                .map(video -> Pair.of(video.getMcId(), video.getScId()))
                .distinct()
                .toList();

        List<Long> videoIds = videoList.stream()
                .map(Video::getVid)
                .toList();

        // 异步并行查询分类、统计、用户信息
        CompletableFuture<Map<Pair<String, String>, Category>> categoryFuture = CompletableFuture.supplyAsync(() -> {
            // 安全构造IN条件
            LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
            wrapper.nested(q -> {
                mcScIdPairs.forEach(pair ->
                        q.or(w ->
                                w.eq(Category::getMcId, pair.getKey())
                                        .eq(Category::getScId, pair.getValue())
                        )
                );
            });
            return categoryService.list(wrapper)
                    .stream()
                    .collect(Collectors.toMap(
                            c -> Pair.of(c.getMcId(), c.getScId()),
                            c -> c
                    ));
        }, asyncExecutor); // 使用自定义线程池

        CompletableFuture<Map<Long, VideoStat>> statFuture = CompletableFuture.supplyAsync(() ->
                videoStatService.listByIds(videoIds)
                        .stream()
                        .collect(Collectors.toMap(
                                VideoStat::getVid,
                                vs -> vs,
                                (existing, replacement) -> existing
                        )), asyncExecutor);

        CompletableFuture<Map<Long, UserDTO>> userFuture = CompletableFuture.supplyAsync(() -> {
            // 调用 Feign 接口获取用户信息
            List<UserDTO> users = userFeignApi.getBatchUserInfo(uids);
            return users.stream()
                    .collect(Collectors.toMap(
                            UserDTO::getUid,
                            user -> user,
                            (existing, replacement) -> existing
                    ));
        }, asyncExecutor);

        // 4. 组合结果
        CompletableFuture<ResultData<List<Map<String, Object>>>> resultFuture = CompletableFuture
                .allOf(categoryFuture, statFuture, userFuture)
                .thenApplyAsync(v -> {
                    Map<Pair<String, String>, Category> categoryMap = categoryFuture.join();
                    Map<Long, VideoStat> statMap = statFuture.join();
                    Map<Long, UserDTO> userMap = userFuture.join();

                    return videoList.stream().map(video -> {
                        Map<String, Object> map = new HashMap<>(4);
                        map.put("video", video);
                        map.put("category",
                                Optional.ofNullable(categoryMap.get(Pair.of(video.getMcId(), video.getScId())))
                                        .orElseGet(Category::new));
                        map.put("stat",
                                Optional.ofNullable(statMap.get(video.getVid()))
                                        .orElseGet(() -> {
                                            VideoStat stat = new VideoStat();
                                            stat.setVid(video.getVid());
                                            return stat;
                                        }));
                        map.put("user",
                                Optional.ofNullable(userMap.get(video.getUid()))
                                        .orElse(new UserDTO())
                        );
                        return map;
                    }).collect(Collectors.toList());
                }, asyncExecutor)
                .thenApply(a -> ResultData.success(a, "推荐视频加载成功"))
                .exceptionally(e -> {
                    log.error("推荐视频加载失败", e);
                    return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "数据加载失败");
                });

//        long end = System.currentTimeMillis();
//        log.info("异步总耗时：{}ms ", end - start);
        return resultFuture.join();
    }

    /**
     * 保存视频
     *
     * @param uid   用户id
     * @param video 视频对象
     */
    @Transactional
    @Override
    public void saveVideo(String uid, Video video) {
        video.setUid(Long.valueOf(uid));
        if (videoMapper.insert(video) == 1 && videoStatService.saveVideoStat(video.getVid()) == 1) {
            ResultData.success("保存视频成功");
        } else {
            ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "保存视频失败");
        }
    }

    /**
     * 根据视频id获取视频
     *
     * @param vid 视频id
     * @return ResultData对象
     */
    @Override
    public ResultData<HashMap<String, Object>> getVideoById(Integer vid) {
        Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>().eq(Video::getVid, vid));
        if (Objects.nonNull(video)) {
            VideoStat videoStat = videoStatService.getVideoStatByVid(vid);
            Category category = categoryService.getCategoryById(video.getMcId(), video.getScId());
            HashMap<String, Object> map = new HashMap<>();
            if (Objects.nonNull(videoStat) && Objects.nonNull(category)) {
                map.put("video", video);
                map.put("stat", videoStat);
                map.put("category", category);
                map.put("user", userFeignApi.getUserByUid(video.getUid()));
                return ResultData.success(map, "获取视频信息成功");
            }
        }
        return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST);
    }
}
