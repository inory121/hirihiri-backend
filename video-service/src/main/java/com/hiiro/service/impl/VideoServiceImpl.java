package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.document.VideoDocument;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.CategoryService;
import com.hiiro.service.VideoService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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
    private VideoStatService videoStatService;
    @Resource
    private VideoMapper videoMapper;
    @Resource
    private CategoryService categoryService;
    @Resource(name = "videoAsyncExecutor")
    private Executor asyncExecutor;
    @Resource
    private UserFeignApi userFeignApi;
    @Resource
    ElasticsearchOperations esOperations;

    // 校验并构建分页对象
    private Page<Video> validateAndBuildPage(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) pageNum = 1;
        else if (pageNum > 100) pageNum = 100;

        if (pageSize == null || pageSize < 1) pageSize = 10;
        else if (pageSize > 50) pageSize = 50;

        return new Page<>(pageNum, pageSize);
    }

    // 处理分页查询核心逻辑
    private ResultData<List<Map<String, Object>>> processVideoPage(IPage<Video> videoPage) {
        long start = System.currentTimeMillis();
        List<Video> videoList = videoPage.getRecords();
        if (videoList.isEmpty()) {
            return ResultData.success(Collections.emptyList(), "视频列表为空");
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
            wrapper.nested(queryWrapper -> mcScIdPairs.forEach(pair ->
                    queryWrapper.or(queryWrapper1 ->
                            queryWrapper1.eq(Category::getMcId, pair.getKey())
                                    .eq(Category::getScId, pair.getValue())
                    )
            ));
            return categoryService.list(wrapper)
                    .stream()
                    .collect(Collectors.toMap(
                            category -> Pair.of(category.getMcId(), category.getScId()),
                            category -> category
                    ));
        }, asyncExecutor); // 使用自定义线程池

        CompletableFuture<Map<Long, VideoStat>> statFuture = CompletableFuture.supplyAsync(() ->
                videoStatService.listByIds(videoIds)
                        .stream()
                        .collect(Collectors.toMap(
                                VideoStat::getVid,
                                videoStat -> videoStat,
                                (existing, replacement) -> existing
                        )), asyncExecutor);

        // 在主线程捕获请求上下文
        // openfeign在异步线程中执行时，RequestContextHolder 无法传递原始请求上下文，所以在提交异步任务前，手动捕获并传递请求上下文
        RequestAttributes mainThreadAttributes = RequestContextHolder.getRequestAttributes();
        CompletableFuture<Map<Long, UserDTO>> userFuture = CompletableFuture.supplyAsync(() -> {
            // 将主线程上下文绑定到子线程
            RequestContextHolder.setRequestAttributes(mainThreadAttributes);
            try {
                return userFeignApi.getBatchUserInfo(uids)
                        .stream()
                        .collect(Collectors.toMap(UserDTO::getUid, userDTO -> userDTO));
            } finally {
                // 清理子线程上下文
                RequestContextHolder.resetRequestAttributes();
            }
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
                .thenApply(a -> ResultData.success(a, "获取视频成功"))
                .exceptionally(e -> {
                    log.error("视频加载失败", e);
                    return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "数据加载失败");
                });

        return resultFuture.thenApply(result -> {
            long end = System.currentTimeMillis();
            log.info("获取视频耗时：{}ms ", end - start);
            return result;
        }).join();
    }

    /**
     * 获取推荐视频
     *
     * @return 推荐视频列表
     */
    @Override
    public ResultData<List<Map<String, Object>>> getRecommendVideos(Integer pageNum, Integer pageSize) {
        Page<Video> page = validateAndBuildPage(pageNum, pageSize);
        IPage<Video> videoPage = new LambdaQueryChainWrapper<>(videoMapper)
                .eq(Video::getStatus, 1)
                .page(page);
        return processVideoPage(videoPage);
    }

    /**
     * 获取所有视频
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return ResultData对象
     */
    @Override
    public ResultData<List<Map<String, Object>>> getAllVideos(Integer pageNum, Integer pageSize) {
        Page<Video> page = validateAndBuildPage(pageNum, pageSize);
        IPage<Video> videoPage = new LambdaQueryChainWrapper<>(videoMapper).page(page);
        return processVideoPage(videoPage);
    }

    /**
     * 保存视频
     *
     * @param uid   用户id
     * @param video 视频对象
     * @return 保存视频是否成功
     */
    @Transactional
    @Override
    public boolean saveVideo(String uid, Video video) {
        video.setUid(Long.valueOf(uid));
        if (videoMapper.insert(video) == 1 && videoStatService.saveVideoStat(video.getVid()) == 1) {
            try {
                esOperations.save(BeanUtil.copyProperties(video, VideoDocument.class));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
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
                Long uid = video.getUid();
                UserDTO user = userFeignApi.getUserByUid(uid).getData();
                map.put("user", user);
                return ResultData.success(map, "获取视频信息成功");
            }
        }
        return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST);
    }

    /**
     * 更新视频
     *
     * @param video     视频对象
     * @param coverFile 封面文件
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<Video> updateVideo(Video video, MultipartFile coverFile) {
        // 判断视频是否存在
        Video originalVideo = videoMapper.selectById(video.getVid());
        if (Objects.isNull(originalVideo)) {
            return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST);
        }
        return videoMapper.updateById(video) == 1 ? ResultData.success(video, "更新视频成功") :
                ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "更新视频失败");
    }

    /**
     * 逻辑删除视频
     *
     * @param vid    视频id
     * @param status 状态
     * @return ResultData对象
     */
    @Override
    public ResultData<Video> updateVideoStatus(Long vid, Byte status) {
        LambdaUpdateChainWrapper<Video> chainWrapper = new LambdaUpdateChainWrapper<>(videoMapper).eq(Video::getVid, vid);
        if (status == 3) {
            return chainWrapper.set(Video::getStatus, status).set(Video::getDelDate, LocalDateTime.now()).update() ?
                    ResultData.success("删除视频成功") : ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "删除视频失败");
        } else if (status == 1 || status == 2) {
            return chainWrapper.set(Video::getStatus, status).set(Video::getDelDate, null).update() ?
                    ResultData.success("更新视频成功") : ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "更新视频失败");
        }
        return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "请求参数有误");
    }

    /**
     * 搜索视频
     *
     * @param keyword  关键字
     * @param pageNum  页码
     * @param pageSize 页大小
     * @return ResultData对象
     */
    @Override
    public ResultData<List<Map<String, Object>>> searchVideos(String keyword, Integer pageNum, Integer pageSize) {
        // 1. 构建 NativeQuery，多字段匹配 + 高亮
        HighlightParameters highlightParams = HighlightParameters.builder()
                .withPreTags("<em class='keyword'>")
                .withPostTags("</em>")
                .build();

        List<HighlightField> highlightFields = List.of(
                new HighlightField("title") // 只保留title的高亮
        );

        Highlight highlight = new Highlight(highlightParams, highlightFields);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .should(s -> s.wildcard(w -> w  // 通配符查询
                                .field("title")
                                .value("*" + keyword + "*")
                                .caseInsensitive(true)
                                .boost(5.0f)))
                        .should(s -> s.multiMatch(multi -> multi
                                .query(keyword)
                                .fields("title^3", "descr", "tags^2")
                                .type(TextQueryType.Phrase)
                        ))
                        .should(s -> s.multiMatch(multi -> multi
                                .query(keyword)
                                .fields("title.pinyin", "descr.pinyin", "tags.pinyin")
                                .type(TextQueryType.Phrase)
                        ))
                        .minimumShouldMatch("1") // 至少匹配一个条件
                ))
                .withSort(s -> s.field(f -> f
                        .field("_score")
                        .order(SortOrder.Desc)
                ))
                .withHighlightQuery(new HighlightQuery(highlight, VideoDocument.class))
                .build();

        // 2. 执行搜索
        SearchHits<VideoDocument> search = esOperations.search(query, VideoDocument.class);
        if (search.getTotalHits() == 0) {
            return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST);
        }
//        search.getSearchHits().forEach(hit -> {
//            System.out.println("视频" + hit.getContent().getVid() + " 得分：" + hit.getScore());
//        });
        // 3. 按ES顺序收集结果（LinkedHashMap保持顺序）
        LinkedHashMap<Long, SearchHit<VideoDocument>> orderedHits = new LinkedHashMap<>();
        Map<Long, String> titleHighlightMap = new HashMap<>(); // 高亮存储

        search.get().forEach(hit -> {
            Long vid = hit.getContent().getVid();
            orderedHits.put(vid, hit);
            // 处理高亮
            if (hit.getHighlightFields().containsKey("title")) {
                List<String> highlights = hit.getHighlightFields().get("title");
                if (!highlights.isEmpty()) {
                    titleHighlightMap.put(vid, highlights.get(0));
                }
            }
        });

        // 4. 按ES顺序查询数据库
        List<Video> videos = new LambdaQueryChainWrapper<>(videoMapper)
                .in(Video::getVid, new ArrayList<>(orderedHits.keySet()))
                .list();

        // 5. 按ES顺序重组结果
        List<Video> orderedVideos = new ArrayList<>();
        orderedHits.forEach((vid, hit) ->
                videos.stream()
                        .filter(v -> v.getVid().equals(vid))
                        .findFirst()
                        .ifPresent(video -> {
                            // 应用高亮标题
                            if (titleHighlightMap.containsKey(vid)) {
                                video.setTitle(titleHighlightMap.get(vid));
                            }
                            orderedVideos.add(video);
                        })
        );

        // 6. 分页处理
        Page<Video> page = validateAndBuildPage(pageNum, pageSize);
        page.setRecords(orderedVideos);
        return processVideoPage(page);
    }

}
