package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.RecommendFeedDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.*;
import com.hiiro.service.CategoryService;
import com.hiiro.service.RecommendService;
import com.hiiro.service.cache.UserCacheService;
import com.hiiro.service.cache.VideoStatCacheService;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 推荐服务实现
 * MVP 阶段：热度召回 + 兴趣召回 + 关注作者召回 + 统一评分 + 重排
 *
 * @author hiiro
 * @since 2025-07-23
 */
@Slf4j
@Service
public class RecommendServiceImpl implements RecommendService {

    private record CategoryKey(String mcId, String scId) {}

    /**
     * 推荐流缓存对象，包含 requestId 和候选 vid 列表
     */
    private record FeedCache(String requestId, List<Long> vids) {}

    private static final String REDIS_KEY_PROFILE = "recommend:profile:";
    private static final String REDIS_KEY_BLOCKED_VID = "recommend:blocked:vid:";
    private static final String REDIS_KEY_BLOCKED_AUTHOR = "recommend:blocked:author:";
    private static final String REDIS_KEY_FEED_CACHE = "recommend:feed:cache:";
    private static final int HOT_CANDIDATE_LIMIT = 200;
    private static final int INTEREST_CANDIDATE_LIMIT = 200;
    private static final int FOLLOW_CANDIDATE_LIMIT = 200;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_CANDIDATES = 400;
    private static final double NEW_VIDEO_HOURS = 24 * 7;
    private static final double FULLY_WATCHED_RATIO = 0.9;
    private static final long FEED_CACHE_TTL_MINUTES = 10;

    // 统一评分权重
    private static final double W_INTEREST = 0.40;
    private static final double W_QUALITY = 0.20;
    private static final double W_FRESHNESS = 0.15;
    private static final double W_ENGAGEMENT = 0.15;
    private static final double W_AUTHOR = 0.05;
    private static final double W_EXPLORE = 0.05;

    // 画像行为权重
    private static final double P_FAVORITE = 5.0;
    private static final double P_COIN = 4.0;
    private static final double P_LIKE = 3.0;
    private static final double P_VALID_WATCH = 2.0;
    private static final double P_CLICK = 1.0;
    private static final double P_DISLIKE = -8.0;

    @Resource
    private VideoMapper videoMapper;
    @Resource
    private VideoStatMapper videoStatMapper;
    @Resource
    private VideoDislikeMapper videoDislikeMapper;
    @Resource
    private UserBrowseHistoryMapper userBrowseHistoryMapper;
    @Resource
    private RecommendEventMapper recommendEventMapper;
    @Resource
    private UserFeignApi userFeignApi;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper feedCacheMapper = new ObjectMapper();
    @Resource
    private VideoStatCacheService videoStatCacheService;
    @Resource
    private UserCacheService userCacheService;
    @Resource
    private CategoryService categoryService;
    @Resource(name = "videoAsyncExecutor")
    private Executor asyncExecutor;

    // ==================== 推荐流主接口 ====================

    @Override
    public ResultData<RecommendFeedDTO> getFeed(String cursor, String scene, Integer limit, Long uid) {
        long t0 = System.nanoTime();
        try {
            limit = (limit == null || limit < 1) ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
            long effectiveUid = uid != null ? uid : 0L;

            List<Long> rerankedVids;
            Map<Long, Video> videoMap;
            Map<Long, VideoStat> statMap;
            String requestId;
            String cacheKey;

            if (cursor != null && !cursor.isEmpty()) {
                // 翻页：从 Redis 缓存读取上次计算的候选集
                cacheKey = parseCacheKeyFromCursor(cursor);
                if (cacheKey != null) {
                    String redisKey = REDIS_KEY_FEED_CACHE + effectiveUid + ":" + cacheKey;
                    FeedCache feedCache = readFeedCache(redisKey);
                    if (feedCache != null) {
                        requestId = feedCache.requestId();
                        rerankedVids = feedCache.vids();
                        // 从缓存中恢复 videoMap 和 statMap 需要重新加载
                        videoMap = loadVideoMap(rerankedVids);
                        statMap = videoStatCacheService.getBatch(rerankedVids);
                    } else {
                        // 缓存失效，重新计算（cursor 清空）
                        return getFeed(null, scene, limit, uid);
                    }
                } else {
                    return getFeed(null, scene, limit, uid);
                }
            } else {
                // 首次请求：执行完整召回 → 评分 → 重排
                requestId = UUID.randomUUID().toString().replace("-", "");
                cacheKey = UUID.randomUUID().toString().replace("-", "");

                // 1. 三路召回
                Set<Long> candidateVids = new LinkedHashSet<>();
                videoMap = new HashMap<>();

                // 1a. 热度召回
                List<Video> hotVideos = recallHot();
                for (Video v : hotVideos) {
                    candidateVids.add(v.getVid());
                    videoMap.put(v.getVid(), v);
                }

                // 1b. 兴趣召回 + 1c. 关注作者召回（已登录用户）
                if (uid != null) {
                    List<Video> interestVideos = recallInterest(uid);
                    for (Video v : interestVideos) {
                        candidateVids.add(v.getVid());
                        videoMap.put(v.getVid(), v);
                    }

                    List<Video> followVideos = recallFollowing(uid);
                    for (Video v : followVideos) {
                        candidateVids.add(v.getVid());
                        videoMap.put(v.getVid(), v);
                    }
                }

                if (candidateVids.isEmpty()) {
                    RecommendFeedDTO empty = new RecommendFeedDTO();
                    empty.setItems(Collections.emptyList());
                    empty.setNextCursor(null);
                    empty.setRequestId(requestId);
                    return ResultData.success(empty);
                }

                // 2. 加载统计数据
                List<Long> vidList = new ArrayList<>(candidateVids);
                statMap = videoStatCacheService.getBatch(vidList);

                // 3. 加载用户画像 & 过滤集合
                Map<String, Double> profile = uid != null ? loadProfile(uid) : null;
                Set<Long> followingUids = uid != null ? loadFollowingUids(uid) : Collections.emptySet();
                Set<Long> dislikedVids = uid != null ? loadDislikedVids(uid) : Collections.emptySet();
                Set<Long> fullyWatchedVids = uid != null ? loadFullyWatchedVids(uid) : Collections.emptySet();
                Set<Long> blockedVids = uid != null ? loadBlockedVids(uid) : Collections.emptySet();
                Set<Long> blockedAuthors = uid != null ? loadBlockedAuthors(uid) : Collections.emptySet();

                // 4. 统一评分
                Map<Long, Double> scores = new HashMap<>();
                for (Long vid : candidateVids) {
                    Video v = videoMap.get(vid);
                    VideoStat s = statMap.get(vid);
                    if (v == null) continue;
                    scores.put(vid, calcUnifiedScore(v, s, profile, followingUids));
                }

                // 5. 按评分排序
                List<Long> sortedVids = scores.entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .map(Map.Entry::getKey)
                        .toList();

                // 6. 重排（传入所有过滤集）
                rerankedVids = reRank(sortedVids, videoMap, dislikedVids, fullyWatchedVids,
                        blockedVids, blockedAuthors);

                // 7. 缓存候选集到 Redis（绑定用户），供翻页使用
                writeFeedCache(REDIS_KEY_FEED_CACHE + effectiveUid + ":" + cacheKey,
                        new FeedCache(requestId, rerankedVids));
            }

            // 8. 游标分页
            int offset = parseCursorOffset(cursor);
            int end = Math.min(offset + limit, rerankedVids.size());
            if (offset >= rerankedVids.size()) {
                RecommendFeedDTO empty = new RecommendFeedDTO();
                empty.setItems(Collections.emptyList());
                empty.setNextCursor(null);
                empty.setRequestId(requestId);
                return ResultData.success(empty);
            }
            List<Long> pageVids = rerankedVids.subList(offset, end);

            // 9. 组装视频详情
            List<Video> pageVideos = pageVids.stream()
                    .map(videoMap::get)
                    .filter(Objects::nonNull)
                    .toList();
            List<Map<String, Object>> items = assembleVideoList(pageVideos, statMap);

            // 10. 生成返回，游标编码为 cacheKey:offset
            String nextCursor = end < rerankedVids.size() && cacheKey != null
                    ? cacheKey + ":" + end
                    : null;

            RecommendFeedDTO dto = new RecommendFeedDTO();
            dto.setItems(items);
            dto.setNextCursor(nextCursor);
            dto.setRequestId(requestId);

            return ResultData.success(dto);
        } finally {
            log.info("getFeed uid={} cursor={} end2end={}ms", uid, cursor,
                    (System.nanoTime() - t0) / 1_000_000);
        }
    }

    // ==================== 三路召回 ====================

    private List<Video> recallHot() {
        return new LambdaQueryChainWrapper<>(videoMapper)
                .eq(Video::getStatus, (byte) 1)
                .orderByDesc(Video::getHotScore)
                .last("LIMIT " + HOT_CANDIDATE_LIMIT)
                .list();
    }

    /**
     * 兴趣召回：按主分区、子分区、标签、作者四个维度召回
     */
    private List<Video> recallInterest(Long uid) {
        Map<String, Double> profile = loadProfile(uid);
        if (profile == null || profile.isEmpty()) {
            return Collections.emptyList();
        }

        // 取 Top 5 偏好主分区
        List<String> topMcIds = profile.entrySet().stream()
                .filter(e -> e.getKey().startsWith("mc:"))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> e.getKey().substring(3))
                .toList();

        // 取 Top 5 偏好子分区
        List<String> topScIds = profile.entrySet().stream()
                .filter(e -> e.getKey().startsWith("sc:"))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> e.getKey().substring(3))
                .toList();

        // 取 Top 10 偏好标签
        List<String> topTags = profile.entrySet().stream()
                .filter(e -> e.getKey().startsWith("tag:"))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> e.getKey().substring(4))
                .toList();

        // 取 Top 5 偏好作者
        List<Long> topAuthors = profile.entrySet().stream()
                .filter(e -> e.getKey().startsWith("author:"))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> Long.valueOf(e.getKey().substring(7)))
                .toList();

        Set<Video> result = new LinkedHashSet<>();

        // 按主分区召回
        if (!topMcIds.isEmpty()) {
            result.addAll(new LambdaQueryChainWrapper<>(videoMapper)
                    .eq(Video::getStatus, (byte) 1)
                    .in(Video::getMcId, topMcIds)
                    .orderByDesc(Video::getHotScore)
                    .last("LIMIT " + INTEREST_CANDIDATE_LIMIT)
                    .list());
        }

        // 按子分区召回
        if (!topScIds.isEmpty()) {
            result.addAll(new LambdaQueryChainWrapper<>(videoMapper)
                    .eq(Video::getStatus, (byte) 1)
                    .in(Video::getScId, topScIds)
                    .orderByDesc(Video::getHotScore)
                    .last("LIMIT 60")
                    .list());
        }

        // 按标签召回（LIKE 模糊匹配）
        if (!topTags.isEmpty()) {
            LambdaQueryChainWrapper<Video> tagWrapper = new LambdaQueryChainWrapper<>(videoMapper)
                    .eq(Video::getStatus, (byte) 1);
            tagWrapper.and(w -> {
                for (int i = 0; i < topTags.size(); i++) {
                    String tag = topTags.get(i);
                    if (i == 0) {
                        w.like(Video::getTags, tag);
                    } else {
                        w.or().like(Video::getTags, tag);
                    }
                }
            });
            result.addAll(tagWrapper.orderByDesc(Video::getHotScore).last("LIMIT 60").list());
        }

        // 按偏好作者召回
        if (!topAuthors.isEmpty()) {
            result.addAll(new LambdaQueryChainWrapper<>(videoMapper)
                    .eq(Video::getStatus, (byte) 1)
                    .in(Video::getUid, topAuthors)
                    .orderByDesc(Video::getHotScore)
                    .last("LIMIT 40")
                    .list());
        }

        return new ArrayList<>(result);
    }

    private List<Video> recallFollowing(Long uid) {
        try {
            ResultData<List<Long>> resp = userFeignApi.getFollowingUids(uid);
            List<Long> followingUids = (resp != null && resp.getData() != null) ? resp.getData() : Collections.emptyList();
            if (followingUids.isEmpty()) {
                return Collections.emptyList();
            }
            return new LambdaQueryChainWrapper<>(videoMapper)
                    .eq(Video::getStatus, (byte) 1)
                    .in(Video::getUid, followingUids)
                    .orderByDesc(Video::getCreateTime)
                    .last("LIMIT " + FOLLOW_CANDIDATE_LIMIT)
                    .list();
        } catch (Exception e) {
            log.warn("获取关注列表失败, uid={}", uid, e);
            return Collections.emptyList();
        }
    }

    // ==================== 统一评分 ====================

    private double calcUnifiedScore(Video video, VideoStat stat,
                                    Map<String, Double> profile, Set<Long> followingUids) {
        // 兴趣匹配
        double interestMatch = 0;
        if (profile != null) {
            double mcScore = profile.getOrDefault("mc:" + video.getMcId(), 0.0);
            double scScore = profile.getOrDefault("sc:" + video.getScId(), 0.0);
            double tagScore = 0;
            if (video.getTags() != null) {
                for (String tag : video.getTags().split("\\r?\\n")) {
                    tag = tag.trim();
                    if (!tag.isEmpty()) {
                        tagScore = Math.max(tagScore, profile.getOrDefault("tag:" + tag, 0.0));
                    }
                }
            }
            double authorScore = profile.getOrDefault("author:" + video.getUid(), 0.0);
            double raw = mcScore * 0.3 + scScore * 0.3 + tagScore * 0.2 + authorScore * 0.2;
            interestMatch = Math.min(1.0, raw / 30.0);
        }

        // 内容质量（基于互动率）
        int view = stat != null && stat.getView() != null ? stat.getView() : 0;
        int like = stat != null && stat.getLike() != null ? stat.getLike() : 0;
        int favorite = stat != null && stat.getFavorite() != null ? stat.getFavorite() : 0;
        int coin = stat != null && stat.getCoin() != null ? stat.getCoin() : 0;
        int dislike = stat != null && stat.getDislike() != null ? stat.getDislike() : 0;

        double quality = 0;
        if (view > 0) {
            double likeRate = (double) like / view;
            double favRate = (double) favorite / view;
            double coinRate = (double) coin / view;
            double disRate = (double) dislike / view;
            double raw = Math.log1p(view) / 10.0
                    + 3.0 * likeRate
                    + 5.0 * favRate
                    + 4.0 * coinRate
                    - 6.0 * disRate;
            quality = Math.max(0, Math.min(1.0, raw / 5.0));
        }

        // 新鲜度
        double freshness = 1.0;
        if (video.getCreateTime() != null) {
            long hours = ChronoUnit.HOURS.between(video.getCreateTime(), LocalDateTime.now());
            freshness = Math.exp(-hours / 168.0);
        }

        // 互动热度
        double engagement = Math.min(1.0, view / 10000.0);

        // 作者亲和度
        double authorAffinity = followingUids.contains(video.getUid()) ? 1.0 : 0.0;

        // 探索分（新视频）
        boolean isNew = video.getCreateTime() != null
                && ChronoUnit.HOURS.between(video.getCreateTime(), LocalDateTime.now()) < NEW_VIDEO_HOURS;
        double explore = isNew ? 0.5 : 0.0;

        return W_INTEREST * interestMatch
                + W_QUALITY * quality
                + W_FRESHNESS * freshness
                + W_ENGAGEMENT * engagement
                + W_AUTHOR * authorAffinity
                + W_EXPLORE * explore;
    }

    // ==================== 重排 ====================

    /**
     * 重排：过滤 + 多样性 + 探索位
     *
     * @param sortedVids       评分排序后的 vid 列表
     * @param videoMap         vid → Video 映射
     * @param dislikedVids     已点踩的视频集合
     * @param fullyWatchedVids 已完整观看的视频集合
     * @param blockedVids      不感兴趣的视频集合
     * @param blockedAuthors   已屏蔽的作者集合
     */
    private List<Long> reRank(List<Long> sortedVids, Map<Long, Video> videoMap,
                              Set<Long> dislikedVids, Set<Long> fullyWatchedVids,
                              Set<Long> blockedVids, Set<Long> blockedAuthors) {
        List<Long> result = new ArrayList<>();
        Map<Long, Integer> authorCount = new HashMap<>();
        String lastScId = null;
        int newVideoSlots = 2;

        for (Long vid : sortedVids) {
            Video v = videoMap.get(vid);
            if (v == null) continue;

            // 强过滤：点踩、不感兴趣、已看完、屏蔽作者
            if (dislikedVids.contains(vid)) continue;
            if (blockedVids.contains(vid)) continue;
            if (fullyWatchedVids.contains(vid)) continue;
            if (blockedAuthors.contains(v.getUid())) continue;

            // 同作者上限
            int ac = authorCount.getOrDefault(v.getUid(), 0);
            if (ac >= 2) continue;

            // 分区打散：连续两条不能同一子分区（放宽到每3条至少1条不同）
            if (lastScId != null && lastScId.equals(v.getScId()) && result.size() % 3 != 0) {
                continue;
            }

            result.add(vid);
            authorCount.put(v.getUid(), ac + 1);
            lastScId = v.getScId();

            if (result.size() >= MAX_CANDIDATES) break;
        }

        // 探索补位：如果结果中新视频不够，从候选中补充（同样遵守过滤规则）
        long newCount = result.stream()
                .map(videoMap::get)
                .filter(v -> v != null && v.getCreateTime() != null)
                .filter(v -> ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now()) < NEW_VIDEO_HOURS)
                .count();

        if (newCount < newVideoSlots) {
            for (Long vid : sortedVids) {
                if (result.contains(vid)) continue;
                Video v = videoMap.get(vid);
                if (v == null || v.getCreateTime() == null) continue;
                if (ChronoUnit.HOURS.between(v.getCreateTime(), LocalDateTime.now()) >= NEW_VIDEO_HOURS) continue;
                // 探索补位也必须遵守过滤规则
                if (dislikedVids.contains(vid) || blockedVids.contains(vid) || fullyWatchedVids.contains(vid)) continue;
                if (blockedAuthors.contains(v.getUid())) continue;
                if (authorCount.getOrDefault(v.getUid(), 0) >= 2) continue;

                int insertPos = Math.min(result.size(), result.size() / 2 + 1);
                result.add(insertPos, vid);
                authorCount.merge(v.getUid(), 1, Integer::sum);
                if (++newCount >= newVideoSlots) break;
            }
        }

        return result;
    }

    // ==================== 游标解析 ====================

    /**
     * 从游标中解析缓存 key（格式：cacheKey:offset）
     */
    private String parseCacheKeyFromCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        int colonIdx = cursor.lastIndexOf(':');
        if (colonIdx <= 0) {
            return null;
        }
        return cursor.substring(0, colonIdx);
    }

    /**
     * 从游标中解析 offset（格式：cacheKey:offset）
     */
    private int parseCursorOffset(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        int colonIdx = cursor.lastIndexOf(':');
        if (colonIdx <= 0 || colonIdx >= cursor.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(cursor.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ==================== 数据加载 ====================

    /**
     * 写入 FeedCache 到 Redis（JSON 序列化，绕过 Jackson 多态类型问题）
     */
    private void writeFeedCache(String key, FeedCache cache) {
        try {
            String json = feedCacheMapper.writeValueAsString(cache);
            stringRedisTemplate.opsForValue().set(key, json, FEED_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入推荐缓存失败, key={}", key, e);
        }
    }

    /**
     * 从 Redis 读取 FeedCache（JSON 反序列化）
     */
    private FeedCache readFeedCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return feedCacheMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("读取推荐缓存失败, key={}", key, e);
            return null;
        }
    }

    private Map<String, Double> loadProfile(Long uid) {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(REDIS_KEY_PROFILE + uid);
            Map<String, Double> profile = new HashMap<>();
            entries.forEach((k, v) -> {
                try {
                    profile.put(k.toString(), Double.parseDouble(v.toString()));
                } catch (Exception ignored) {
                }
            });
            return profile;
        } catch (Exception e) {
            log.warn("加载用户画像失败, uid={}", uid, e);
            return Collections.emptyMap();
        }
    }

    private Set<Long> loadFollowingUids(Long uid) {
        try {
            ResultData<List<Long>> resp = userFeignApi.getFollowingUids(uid);
            List<Long> list = (resp != null && resp.getData() != null) ? resp.getData() : Collections.emptyList();
            return new HashSet<>(list);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private Set<Long> loadDislikedVids(Long uid) {
        List<VideoDislike> list = videoDislikeMapper.selectList(
                new LambdaQueryWrapper<VideoDislike>().eq(VideoDislike::getUid, uid));
        return list.stream().map(VideoDislike::getVid).collect(Collectors.toSet());
    }

    /**
     * 加载已完整观看的视频：progress / duration >= 0.9
     */
    private Set<Long> loadFullyWatchedVids(Long uid) {
        List<UserBrowseHistory> historyList = userBrowseHistoryMapper.selectList(
                new LambdaQueryWrapper<UserBrowseHistory>()
                        .eq(UserBrowseHistory::getUid, uid));
        if (historyList.isEmpty()) {
            return Collections.emptySet();
        }

        // 查询对应视频的时长
        List<Long> vidList = historyList.stream().map(UserBrowseHistory::getVid).distinct().toList();
        List<Video> videos = videoMapper.selectList(
                new LambdaQueryWrapper<Video>()
                        .in(Video::getVid, vidList)
                        .select(Video::getVid, Video::getDuration));
        Map<Long, Double> durationMap = videos.stream()
                .filter(v -> v.getDuration() != null && v.getDuration() > 0)
                .collect(Collectors.toMap(Video::getVid, Video::getDuration));

        return historyList.stream()
                .filter(h -> {
                    if (h.getProgress() == null) return false;
                    Double duration = durationMap.get(h.getVid());
                    if (duration == null || duration <= 0) return false;
                    return h.getProgress() / duration >= FULLY_WATCHED_RATIO;
                })
                .map(UserBrowseHistory::getVid)
                .collect(Collectors.toSet());
    }

    private Set<Long> loadBlockedVids(Long uid) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(REDIS_KEY_BLOCKED_VID + uid);
            if (members == null) return Collections.emptySet();
            return members.stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    private Set<Long> loadBlockedAuthors(Long uid) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(REDIS_KEY_BLOCKED_AUTHOR + uid);
            if (members == null) return Collections.emptySet();
            return members.stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * 批量加载视频信息（用于翻页时从缓存恢复）
     */
    private Map<Long, Video> loadVideoMap(List<Long> vids) {
        if (vids == null || vids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Video> videos = videoMapper.selectList(
                new LambdaQueryWrapper<Video>().in(Video::getVid, vids));
        return videos.stream().collect(Collectors.toMap(Video::getVid, v -> v));
    }

    // ==================== 视频组装 ====================

    private List<Map<String, Object>> assembleVideoList(List<Video> videos, Map<Long, VideoStat> statMap) {
        if (videos.isEmpty()) return Collections.emptyList();

        List<Long> uids = videos.stream().map(Video::getUid).distinct().toList();
        List<String> mcIds = videos.stream().map(Video::getMcId).distinct().toList();
        List<String> scIds = videos.stream().map(Video::getScId).distinct().toList();

        CompletableFuture<Map<Long, UserDTO>> userFuture = CompletableFuture.supplyAsync(
                () -> userCacheService.getBatch(uids), asyncExecutor)
                .completeOnTimeout(Collections.emptyMap(), 300, TimeUnit.MILLISECONDS);

        CompletableFuture<Map<CategoryKey, Category>> categoryFuture = CompletableFuture.supplyAsync(() -> {
            List<Category> cats = categoryService.list();
            return cats.stream()
                    .filter(c -> mcIds.contains(c.getMcId()) && scIds.contains(c.getScId()))
                    .collect(Collectors.toMap(c -> new CategoryKey(c.getMcId(), c.getScId()), c -> c));
        }, asyncExecutor).completeOnTimeout(Collections.emptyMap(), 300, TimeUnit.MILLISECONDS);

        CompletableFuture.allOf(userFuture, categoryFuture).join();

        Map<Long, UserDTO> userMap = userFuture.join();
        Map<CategoryKey, Category> categoryMap = categoryFuture.join();

        List<Map<String, Object>> result = new ArrayList<>(videos.size());
        for (Video video : videos) {
            Map<String, Object> map = new HashMap<>(8);
            map.put("video", video);
            map.put("stat", Optional.ofNullable(statMap.get(video.getVid())).orElseGet(() -> {
                VideoStat s = new VideoStat();
                s.setVid(video.getVid());
                return s;
            }));
            map.put("category", Optional.ofNullable(categoryMap.get(new CategoryKey(video.getMcId(), video.getScId())))
                    .orElseGet(Category::new));
            map.put("user", Optional.ofNullable(userMap.get(video.getUid())).orElse(new UserDTO()));
            result.add(map);
        }
        return result;
    }

    // ==================== 事件上报 ====================

    // 允许的事件类型白名单
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            "impression", "click", "watch_progress", "dislike"
    );
    private static final int MAX_BATCH_SIZE = 50;

    @Override
    @Transactional
    public ResultData<String> reportEvents(List<RecommendEvent> events, Long uid) {
        if (events == null || events.isEmpty()) {
            return ResultData.success("无事件上报");
        }
        if (events.size() > MAX_BATCH_SIZE) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "每批最多上报 " + MAX_BATCH_SIZE + " 条事件");
        }

        LocalDateTime now = LocalDateTime.now();
        int successCount = 0;
        int duplicateCount = 0;
        int invalidCount = 0;
        List<RecommendEvent> acceptedEvents = new ArrayList<>();

        for (RecommendEvent event : events) {
            // 1. 强制使用认证上下文的 uid
            event.setUid(uid);

            // 2. 未登录用户的 impression/click 不落库（无画像价值）
            if (uid == null && ("impression".equals(event.getEventType()) || "click".equals(event.getEventType()))) {
                continue;
            }

            // 3. 校验 eventId（必须由客户端提供）
            if (event.getEventId() == null || event.getEventId().isEmpty()) {
                invalidCount++;
                log.warn("事件缺少 eventId，跳过");
                continue;
            }

            // 3. 校验 eventType 白名单
            if (event.getEventType() == null || !ALLOWED_EVENT_TYPES.contains(event.getEventType())) {
                invalidCount++;
                log.warn("非法事件类型: {}", event.getEventType());
                continue;
            }

            // 4. 校验 vid 存在且视频已审核
            if (event.getVid() == null || event.getVid() <= 0) {
                invalidCount++;
                log.warn("事件缺少合法 vid");
                continue;
            }
            Video video = videoMapper.selectById(event.getVid());
            if (video == null || video.getStatus() == null || video.getStatus() != 1) {
                invalidCount++;
                log.warn("视频不存在或未审核, vid={}", event.getVid());
                continue;
            }

            // 5. 校验 requestId（impression/click 必须带）
            if (("impression".equals(event.getEventType()) || "click".equals(event.getEventType()))
                    && (event.getRequestId() == null || event.getRequestId().isEmpty())) {
                invalidCount++;
                log.warn("impression/click 事件缺少 requestId");
                continue;
            }

            // 6. 校验数值范围
            if (event.getPosition() != null && event.getPosition() < 0) {
                invalidCount++;
                continue;
            }
            if (event.getWatchSeconds() != null && event.getWatchSeconds() < 0) {
                invalidCount++;
                continue;
            }
            if (event.getProgressRatio() != null) {
                if (event.getProgressRatio().doubleValue() < 0 || event.getProgressRatio().doubleValue() > 1) {
                    invalidCount++;
                    continue;
                }
            }

            // 7. 时间窗口校验（不允许未来时间，不允许超过7天的历史）
            if (event.getEventTime() != null) {
                if (event.getEventTime().isAfter(now.plusMinutes(1))) {
                    invalidCount++;
                    continue;
                }
                if (event.getEventTime().isBefore(now.minusDays(7))) {
                    invalidCount++;
                    continue;
                }
            } else {
                event.setEventTime(now);
            }
            if (event.getCreateTime() == null) {
                event.setCreateTime(now);
            }

            try {
                recommendEventMapper.insert(event);
                successCount++;
                acceptedEvents.add(event);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                duplicateCount++;
                log.debug("重复事件上报, eventId={}", event.getEventId());
            }
        }

        // 异步更新用户画像（仅处理实际插入成功的事件）
        if (!acceptedEvents.isEmpty()) {
            CompletableFuture.runAsync(() -> updateProfileFromEvents(acceptedEvents), asyncExecutor);
        }

        if (invalidCount > 0 || duplicateCount > 0) {
            return ResultData.success("上报成功 " + successCount + " 条"
                    + (duplicateCount > 0 ? "，忽略重复 " + duplicateCount + " 条" : "")
                    + (invalidCount > 0 ? "，非法参数 " + invalidCount + " 条" : ""));
        }
        return ResultData.success("上报成功 " + successCount + " 条");
    }

    @Override
    public ResultData<String> revokeDislike(Long uid, Long vid, String requestId, String scene) {
        if (vid == null || requestId == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数缺失");
        }
        // 删除该用户对该视频的 dislike 事件
        QueryWrapper<RecommendEvent> wrapper = new QueryWrapper<>();
        wrapper.eq("vid", vid)
                .eq("event_type", "dislike")
                .eq("request_id", requestId);
        if (uid != null) {
            wrapper.eq("uid", uid);
        }
        if (scene != null) {
            wrapper.eq("scene", scene);
        }
        int deleted = recommendEventMapper.delete(wrapper);

        // 回滚用户画像中的 dislike 扣分
        if (deleted > 0 && uid != null) {
            String profileKey = REDIS_KEY_PROFILE + uid;
            // 获取视频信息以回滚分区和作者画像
            Video video = videoMapper.selectById(vid);
            if (video != null) {
                String mcKey = "mc:" + video.getMcId();
                String scKey = "sc:" + video.getScId();
                String authorKey = "author:" + video.getUid();
                // dislike 权重为 -8，撤销时加回 8
                redisTemplate.opsForHash().increment(profileKey, mcKey, 8.0);
                redisTemplate.opsForHash().increment(profileKey, scKey, 8.0);
                redisTemplate.opsForHash().increment(profileKey, authorKey, 16.0); // 作者双倍
            }
        }
        return ResultData.success("已撤销 " + deleted + " 条点踩记录");
    }

    private void updateProfileFromEvents(List<RecommendEvent> events) {
        for (RecommendEvent event : events) {
            if (event.getUid() == null) continue;
            String key = REDIS_KEY_PROFILE + event.getUid();
            String type = event.getEventType();
            double weight = switch (type) {
                case "favorite" -> P_FAVORITE;
                case "coin" -> P_COIN;
                case "like" -> P_LIKE;
                case "watch_progress" -> {
                    boolean valid = event.getWatchSeconds() != null && event.getWatchSeconds() >= 10;
                    yield valid ? P_VALID_WATCH : P_CLICK;
                }
                case "click" -> P_CLICK;
                case "dislike" -> P_DISLIKE;
                default -> 0;
            };
            if (weight == 0) continue;

            try {
                Video video = videoMapper.selectById(event.getVid());
                if (video == null) continue;

                redisTemplate.opsForHash().increment(key, "mc:" + video.getMcId(), weight);
                redisTemplate.opsForHash().increment(key, "sc:" + video.getScId(), weight);
                redisTemplate.opsForHash().increment(key, "author:" + video.getUid(), weight);
                if (video.getTags() != null) {
                    for (String tag : video.getTags().split("\\r?\\n")) {
                        tag = tag.trim();
                        if (!tag.isEmpty()) {
                            redisTemplate.opsForHash().increment(key, "tag:" + tag, weight * 0.5);
                        }
                    }
                }
                redisTemplate.expire(key, 7, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("更新用户画像失败, uid={}, vid={}", event.getUid(), event.getVid(), e);
            }
        }
    }

    // ==================== 相关推荐 ====================

    @Override
    public ResultData<List<Map<String, Object>>> getRelatedVideos(Long vid, Integer limit) {
        if (limit == null || limit < 1) limit = 10;
        limit = Math.min(limit, MAX_PAGE_SIZE);
        Video current = videoMapper.selectById(vid);
        if (current == null) {
            return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST);
        }

        LinkedHashMap<Long, Video> result = new LinkedHashMap<>();

        // 1. 同作者视频（优先级最高，最多3个）
        List<Video> authorVideos = new LambdaQueryChainWrapper<>(videoMapper)
                .eq(Video::getStatus, (byte) 1)
                .eq(Video::getUid, current.getUid())
                .ne(Video::getVid, vid)
                .orderByDesc(Video::getHotScore)
                .last("LIMIT 3")
                .list();
        for (Video v : authorVideos) {
            result.put(v.getVid(), v);
        }

        // 2. 同标签视频（主力来源）
        if (current.getTags() != null && !current.getTags().isBlank()) {
            String[] tags = current.getTags().split("\\r?\\n");
            List<String> tagList = Arrays.stream(tags)
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .limit(3)
                    .toList();
            if (!tagList.isEmpty()) {
                LambdaQueryChainWrapper<Video> tagWrapper = new LambdaQueryChainWrapper<>(videoMapper)
                        .eq(Video::getStatus, (byte) 1)
                        .ne(Video::getVid, vid);
                tagWrapper.and(w -> {
                    for (int i = 0; i < tagList.size(); i++) {
                        if (i == 0) {
                            w.like(Video::getTags, tagList.get(i));
                        } else {
                            w.or().like(Video::getTags, tagList.get(i));
                        }
                    }
                });
                List<Video> tagVideos = tagWrapper.orderByDesc(Video::getHotScore).last("LIMIT " + limit).list();
                for (Video v : tagVideos) {
                    result.putIfAbsent(v.getVid(), v);
                }
            }
        }

        // 3. 同子分区视频（补充剩余）
        if (result.size() < limit) {
            List<Video> scVideos = new LambdaQueryChainWrapper<>(videoMapper)
                    .eq(Video::getStatus, (byte) 1)
                    .ne(Video::getVid, vid)
                    .eq(Video::getScId, current.getScId())
                    .orderByDesc(Video::getHotScore)
                    .last("LIMIT " + limit)
                    .list();
            for (Video v : scVideos) {
                result.putIfAbsent(v.getVid(), v);
            }
        }

        // 截断到 limit
        List<Video> related = result.values().stream().limit(limit).toList();
        List<Long> vids = related.stream().map(Video::getVid).toList();
        Map<Long, VideoStat> statMap = videoStatCacheService.getBatch(vids);
        List<Map<String, Object>> items = assembleVideoList(related, statMap);
        return ResultData.success(items);
    }

    // ==================== 负反馈 ====================

    @Override
    public ResultData<String> notInterested(Long uid, Long vid) {
        if (uid == null || vid == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数无效");
        }
        try {
            redisTemplate.opsForSet().add(REDIS_KEY_BLOCKED_VID + uid, String.valueOf(vid));
            redisTemplate.expire(REDIS_KEY_BLOCKED_VID + uid, 30, TimeUnit.DAYS);

            Video video = videoMapper.selectById(vid);
            if (video != null) {
                String key = REDIS_KEY_PROFILE + uid;
                redisTemplate.opsForHash().increment(key, "mc:" + video.getMcId(), P_DISLIKE);
                redisTemplate.opsForHash().increment(key, "sc:" + video.getScId(), P_DISLIKE);
            }
            // 清除用户 feed 缓存，确保下次刷新时过滤掉已标记视频
            clearUserFeedCache(uid);
            return ResultData.success("已标记不感兴趣");
        } catch (Exception e) {
            log.error("标记不感兴趣失败", e);
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "操作失败");
        }
    }

    @Override
    public ResultData<String> blockAuthor(Long uid, Long authorUid) {
        if (uid == null || authorUid == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数无效");
        }
        try {
            redisTemplate.opsForSet().add(REDIS_KEY_BLOCKED_AUTHOR + uid, String.valueOf(authorUid));
            redisTemplate.expire(REDIS_KEY_BLOCKED_AUTHOR + uid, 90, TimeUnit.DAYS);

            String key = REDIS_KEY_PROFILE + uid;
            redisTemplate.opsForHash().increment(key, "author:" + authorUid, P_DISLIKE * 2);
            // 清除用户 feed 缓存，确保下次刷新时过滤掉该作者
            clearUserFeedCache(uid);
            return ResultData.success("已屏蔽该作者");
        } catch (Exception e) {
            log.error("屏蔽作者失败", e);
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "操作失败");
        }
    }

    /**
     * 清除用户的所有 feed 缓存（负反馈后调用）
     */
    private void clearUserFeedCache(Long uid) {
        try {
            long effectiveUid = uid != null ? uid : 0L;
            String pattern = REDIS_KEY_FEED_CACHE + effectiveUid + ":*";
            // 使用 scan 避免 keys 命令阻塞生产环境
            org.springframework.data.redis.core.ScanOptions options =
                    org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(100).build();
            try (var cursor = stringRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    stringRedisTemplate.delete(key);
                }
            }
            log.debug("已清除用户 feed 缓存, uid={}", uid);
        } catch (Exception e) {
            log.warn("清除用户 feed 缓存失败, uid={}", uid, e);
        }
    }
}
