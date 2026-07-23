package com.hiiro.service.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
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
import com.hiiro.service.cache.CategoryCacheService;
import com.hiiro.service.cache.UserCacheService;
import com.hiiro.service.cache.VideoStatCacheService;
import com.hiiro.service.fallback.SentinelFallbackHandlers;
import com.hiiro.utils.RedisUtil;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.PageRequest;
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
import java.util.concurrent.TimeUnit;
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
	@Resource
	private StreamBridge streamBridge;
	@Resource
	private CategoryCacheService categoryCacheService;
	@Resource
	private UserCacheService userCacheService;
	@Resource
	private VideoStatCacheService videoStatCacheService;
	@Resource
	private RedisUtil redisUtil;

	private static final String HOT_SEARCH_KEY = "search:hot:list";
	private static final long HOT_SEARCH_EXPIRE_DAYS = 7;

	// 校验并构建分页对象
	private Page<Video> validateAndBuildPage(Integer pageNum, Integer pageSize) {
		if (pageNum == null || pageNum < 1) pageNum = 1;
		else if (pageNum > 100) pageNum = 100;

		if (pageSize == null || pageSize < 1) pageSize = 10;
		else if (pageSize > 50) pageSize = 50;

		return new Page<>(pageNum, pageSize);
	}

	// 处理分页查询核心逻辑（仅聚合，不做端到端计时）
	private ResultData<List<Map<String, Object>>> processVideoPage(IPage<Video> videoPage) {
		List<Video> videoList = videoPage.getRecords();
		if (videoList.isEmpty()) {
			return ResultData.success(Collections.emptyList(), "视频列表为空");
		}

		// 在获取视频列表后，提取所有用户ID
		List<Long> uids = videoList.stream()
				.map(Video::getUid)
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

		// 异步并行查询分类、统计、用户信息（缓存优先）
		CompletableFuture<Map<Pair<String, String>, Category>> categoryFuture = CompletableFuture.supplyAsync(() ->
				categoryCacheService.getBatch(mcScIdPairs), asyncExecutor)
				.completeOnTimeout(Collections.emptyMap(), 300, TimeUnit.MILLISECONDS);

		CompletableFuture<Map<Long, VideoStat>> statFuture = CompletableFuture.supplyAsync(() ->
				videoStatCacheService.getBatch(videoIds), asyncExecutor)
				.completeOnTimeout(Collections.emptyMap(), 300, TimeUnit.MILLISECONDS);

		// 在主线程捕获请求上下文
		RequestAttributes mainThreadAttributes = RequestContextHolder.getRequestAttributes();
		CompletableFuture<Map<Long, UserDTO>> userFuture = CompletableFuture.supplyAsync(() -> {
			RequestContextHolder.setRequestAttributes(mainThreadAttributes);
			try {
				return userCacheService.getBatch(uids);
			} finally {
				RequestContextHolder.resetRequestAttributes();
			}
		}, asyncExecutor)
                .completeOnTimeout(Collections.emptyMap(), 300, TimeUnit.MILLISECONDS);

		CompletableFuture<ResultData<List<Map<String, Object>>>> resultFuture = CompletableFuture
				.allOf(categoryFuture, statFuture, userFuture)
				.thenApply(v -> {
					Map<Pair<String, String>, Category> categoryMap = categoryFuture.join();
					Map<Long, VideoStat> statMap = statFuture.join();
					Map<Long, UserDTO> userMap = userFuture.join();

					List<Map<String, Object>> list = new ArrayList<>(videoList.size());
					for (Video video : videoList) {
						Map<String, Object> map = new HashMap<>(8);
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
						list.add(map);
					}
					return ResultData.success(list, "获取视频成功");
				})
				.exceptionally(e -> {
					log.error("视频加载失败", e);
					return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "数据加载失败");
				});

		return resultFuture.join();
	}

	/**
	 * 获取推荐视频
	 *
	 * @return 推荐视频列表
	 */
	@Override
	@Timed(value = "video.recommend", percentiles = {0.9, 0.95, 0.99})
	@SentinelResource(value = "video_recommend", fallback = "listFallback", fallbackClass = SentinelFallbackHandlers.class, blockHandler = "listBlocked", blockHandlerClass = SentinelFallbackHandlers.class)
	public ResultData<List<Map<String, Object>>> getRecommendVideos(Integer pageNum, Integer pageSize) {
		long t0 = System.nanoTime();
		try {
			Page<Video> page = validateAndBuildPage(pageNum, pageSize);
			IPage<Video> videoPage = new LambdaQueryChainWrapper<>(videoMapper)
					.eq(Video::getStatus, 1)
					.page(page);
			return processVideoPage(videoPage);
		} finally {
			long ms = (System.nanoTime() - t0) / 1_000_000;
			log.info("getRecommendVideos end2end={}ms", ms);
		}
	}

	/**
	 * 获取所有视频
	 *
	 * @param pageNum  页码
	 * @param pageSize 页大小
	 * @return ResultData对象
	 */
	@Override
	@Timed(value = "video.all", percentiles = {0.9, 0.95, 0.99})
	@SentinelResource(value = "video_all", fallback = "listFallback", fallbackClass = SentinelFallbackHandlers.class, blockHandler = "listBlocked", blockHandlerClass = SentinelFallbackHandlers.class)
	public ResultData<List<Map<String, Object>>> getAllVideos(Integer pageNum, Integer pageSize) {
		long t0 = System.nanoTime();
		try {
			Page<Video> page = validateAndBuildPage(pageNum, pageSize);
			IPage<Video> videoPage = new LambdaQueryChainWrapper<>(videoMapper).page(page);
			return processVideoPage(videoPage);
		} finally {
			long ms = (System.nanoTime() - t0) / 1_000_000;
			log.info("getAllVideos end2end={}ms", ms);
		}
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
		video.setVid(null);
		video.setStatus((byte) 0);
		if (videoMapper.insert(video) == 1 && videoStatService.saveVideoStat(video.getVid()) == 1) {
			Map<String, Object> event = new HashMap<>();
			event.put("type", "created");
			event.put("vid", video.getVid());
			event.put("uid", video.getUid());
			event.put("timestamp", System.currentTimeMillis());
			streamBridge.send("videoEvent-out-0", event);
			return true;
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
	public ResultData<HashMap<String, Object>> getVideoById(Long vid) {
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
				UserDTO userDTO = null;
				try {
					ResultData<UserDTO> resp = userFeignApi.getUserByUid(uid);
					if (resp != null) {
						userDTO = resp.getData();
					}
				} catch (Exception ex) {
					log.warn("获取用户信息降级，uid={}", uid);
				}
				if (userDTO == null) {
					userDTO = new UserDTO();
				}
				map.put("user", userDTO);
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
	@Timed(value = "video.search", percentiles = {0.9, 0.95, 0.99})
	@SentinelResource(value = "video_search", fallback = "searchFallback", fallbackClass = SentinelFallbackHandlers.class, blockHandler = "searchBlocked", blockHandlerClass = SentinelFallbackHandlers.class)
	public ResultData<List<Map<String, Object>>> searchVideos(String keyword, Integer pageNum, Integer pageSize, String order) {
		long t0 = System.nanoTime();
		try {
			redisUtil.zIncrementScore(HOT_SEARCH_KEY, keyword, 1.0);
			redisUtil.setExpire(HOT_SEARCH_KEY, HOT_SEARCH_EXPIRE_DAYS, TimeUnit.DAYS);

			if (pageNum == null || pageNum < 1) pageNum = 1;
			else if (pageNum > 100) pageNum = 100;
			if (pageSize == null || pageSize < 1) pageSize = 10;
			else if (pageSize > 50) pageSize = 50;
			final String sortOrder = (order == null || order.isEmpty()) ? "default" : order;

			int fetchSize = 200;

			HighlightParameters highlightParams = HighlightParameters.builder()
					.withPreTags("<em class='keyword'>")
					.withPostTags("</em>")
					.withNumberOfFragments(0)
					.build();

			List<HighlightField> highlightFields = List.of(
					new HighlightField("title")
			);

			Highlight highlight = new Highlight(highlightParams, highlightFields);

			NativeQuery query = NativeQuery.builder()
					.withQuery(q -> q.bool(b -> b
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
							.minimumShouldMatch("1")
					))
					.withSort(s -> s.field(f -> f
							.field("_score")
							.order(SortOrder.Desc)
					))
					.withHighlightQuery(new HighlightQuery(highlight, VideoDocument.class))
					.withPageable(PageRequest.of(0, fetchSize))
					.build();

			SearchHits<VideoDocument> search = esOperations.search(query, VideoDocument.class);
			if (search.getTotalHits() == 0) {
				return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST);
			}

			LinkedHashMap<Long, SearchHit<VideoDocument>> orderedHits = new LinkedHashMap<>();
			Map<Long, String> titleHighlightMap = new HashMap<>();
			search.get().forEach(hit -> {
				Long vid = hit.getContent().getVid();
				orderedHits.put(vid, hit);
				if (hit.getHighlightFields().containsKey("title")) {
					List<String> highlights = hit.getHighlightFields().get("title");
					if (!highlights.isEmpty()) {
						titleHighlightMap.put(vid, highlights.get(0));
					}
				}
			});

			List<Long> pageVids = new ArrayList<>(orderedHits.keySet());
			if (pageVids.isEmpty()) {
				return ResultData.success(Collections.emptyList(), "视频列表为空");
			}
			List<Video> videos = new LambdaQueryChainWrapper<>(videoMapper)
					.in(Video::getVid, pageVids)
					.list();

			Map<Long, Video> videoMap = videos.stream().collect(Collectors.toMap(Video::getVid, v -> v));

			List<Video> orderedVideos = new ArrayList<>();
			for (Long vid : pageVids) {
				Video v = videoMap.get(vid);
				if (v != null) {
					if (titleHighlightMap.containsKey(vid)) {
						v.setTitle(titleHighlightMap.get(vid));
					}
					orderedVideos.add(v);
				}
			}

			if (!"default".equals(sortOrder)) {
				List<Long> allVids = orderedVideos.stream().map(Video::getVid).toList();
				Map<Long, VideoStat> statMap = videoStatCacheService.getBatch(allVids);

				orderedVideos.sort((a, b) -> {
					VideoStat sa = statMap.getOrDefault(a.getVid(), new VideoStat());
					VideoStat sb = statMap.getOrDefault(b.getVid(), new VideoStat());
					return switch (sortOrder) {
						case "view" -> Integer.compare(
								sb.getView() != null ? sb.getView() : 0,
								sa.getView() != null ? sa.getView() : 0);
						case "danmaku" -> Integer.compare(
								sb.getDanmaku() != null ? sb.getDanmaku() : 0,
								sa.getDanmaku() != null ? sa.getDanmaku() : 0);
						case "favorite" -> Integer.compare(
								sb.getFavorite() != null ? sb.getFavorite() : 0,
								sa.getFavorite() != null ? sa.getFavorite() : 0);
						case "date" -> b.getCreateDate().compareTo(a.getCreateDate());
						default -> 0;
					};
				});
			}

			int fromIndex = (pageNum - 1) * pageSize;
			int toIndex = Math.min(fromIndex + pageSize, orderedVideos.size());
			if (fromIndex >= orderedVideos.size()) {
				return ResultData.success(Collections.emptyList(), "视频列表为空");
			}
			List<Video> pageVideos = orderedVideos.subList(fromIndex, toIndex);

			Page<Video> page = new Page<>(pageNum, pageSize);
			page.setRecords(pageVideos);
			page.setTotal(orderedVideos.size());

			return processVideoPage(page);
		} finally {
			long ms = (System.nanoTime() - t0) / 1_000_000;
			log.info("searchVideos end2end={}ms", ms);
		}
	}

	@Override
	public ResultData<List<String>> getHotSearchList(int limit) {
		try {
			if (limit <= 0 || limit > 50) limit = 10;
			Set<Object> hotSet = redisUtil.zReverseRange(HOT_SEARCH_KEY, 0, limit - 1);
			List<String> hotList = hotSet.stream()
					.map(Object::toString)
					.collect(Collectors.toList());
			return ResultData.success(hotList);
		} catch (Exception e) {
			log.error("获取热搜列表失败", e);
			return ResultData.success(Collections.emptyList());
		}
	}

	@Override
	public ResultData<List<String>> searchSuggest(String keyword, int limit) {
		long t0 = System.nanoTime();
		try {
			if (keyword == null || keyword.trim().isEmpty()) {
				return ResultData.success(Collections.emptyList());
			}
			final int maxSize = (limit <= 0 || limit > 50) ? 10 : limit;
			String kw = keyword.trim();
			String kwLower = kw.toLowerCase();

			LinkedHashSet<String> result = new LinkedHashSet<>();

			// 第一步：从热搜里找前缀匹配的（中文前缀匹配，按热度排序）
			try {
				Set<Object> hotSet = redisUtil.zReverseRange(HOT_SEARCH_KEY, 0, 200);
				for (Object obj : hotSet) {
					String hot = obj.toString();
					if (hot.toLowerCase().startsWith(kwLower)) {
						result.add(hot);
						if (result.size() >= maxSize) {
							return ResultData.success(new ArrayList<>(result));
						}
					}
				}
			} catch (Exception e) {
				log.warn("从热搜获取建议失败", e);
			}

			// 第二步：从 ES tags 字段匹配，提取 tag
			try {
				NativeQuery query = NativeQuery.builder()
						.withQuery(q -> q.bool(b -> b
								.should(s -> s.matchPhrasePrefix(m -> m
										.field("tags")
										.query(kw)
								))
								.should(s -> s.matchPhrasePrefix(m -> m
										.field("title")
										.query(kw)
								))
								.should(s -> s.matchPhrasePrefix(m -> m
										.field("title.pinyin")
										.query(kw)
								))
								.minimumShouldMatch("1")
						))
						.withSort(s -> s.field(f -> f
								.field("_score")
								.order(SortOrder.Desc)
						))
						.withPageable(PageRequest.of(0, 50))
						.build();

				SearchHits<VideoDocument> search = esOperations.search(query, VideoDocument.class);
				if (search.getTotalHits() > 0) {
					search.get().forEach(hit -> {
						VideoDocument doc = hit.getContent();
						if (doc.getTags() != null && !doc.getTags().isEmpty()) {
							String[] tags = doc.getTags().split(",");
							for (String tag : tags) {
								String trimmed = tag.trim();
								if (!trimmed.isEmpty() && trimmed.toLowerCase().startsWith(kwLower)) {
									result.add(trimmed);
								}
							}
						}
					});
				}

				// 还不够的话，从标题里提取前若干字作为补充
				if (result.size() < maxSize && search.getTotalHits() > 0) {
					search.get()
							.map(hit -> hit.getContent().getTitle())
							.filter(Objects::nonNull)
							.distinct()
							.forEach(title -> {
								if (result.size() < maxSize) {
									String shortTitle = title.length() > 20 ? title.substring(0, 20) + "..." : title;
									result.add(shortTitle);
								}
							});
				}
			} catch (Exception e) {
				log.error("从 ES 获取搜索建议失败", e);
			}

			List<String> list = result.stream().limit(maxSize).collect(Collectors.toList());
			return ResultData.success(list);
		} finally {
			long ms = (System.nanoTime() - t0) / 1_000_000;
			log.info("searchSuggest end2end={}ms", ms);
		}
	}

	/**
	 * 按用户ID获取投稿视频
	 *
	 * @param uid      用户ID
	 * @param pageNum  分页页数
	 * @param pageSize 分页大小
	 * @return ResultData对象
	 */
	@Override
	@Timed(value = "video.by_uid", percentiles = {0.9, 0.95, 0.99})
	@SentinelResource(value = "video_by_uid", fallback = "listFallback", fallbackClass = SentinelFallbackHandlers.class, blockHandler = "listBlocked", blockHandlerClass = SentinelFallbackHandlers.class)
	public ResultData<List<Map<String, Object>>> getVideosByUid(Long uid, Integer pageNum, Integer pageSize) {
		long t0 = System.nanoTime();
		try {
			if (uid == null || uid <= 0) {
				return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "用户ID无效");
			}
			Page<Video> page = validateAndBuildPage(pageNum, pageSize);
			IPage<Video> videoPage = new LambdaQueryChainWrapper<>(videoMapper)
					.eq(Video::getUid, uid)
					.eq(Video::getStatus, 1)
					.orderByDesc(Video::getCreateDate)
					.page(page);
			return processVideoPage(videoPage);
		} finally {
			long ms = (System.nanoTime() - t0) / 1_000_000;
			log.info("getVideosByUid uid={} end2end={}ms", uid, ms);
		}
	}

	@Override
	public ResultData<Map<String, Object>> getPinnedVideo(Long uid) {
		if (uid == null || uid <= 0) {
			return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "用户ID无效");
		}
		Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>()
				.eq(Video::getUid, uid)
				.eq(Video::getIsTop, 1)
				.eq(Video::getStatus, 1)
				.last("LIMIT 1"));
		if (video == null) {
			return ResultData.success(null, "无置顶视频");
		}
		VideoStat stat = videoStatService.getVideoStatByVid(video.getVid());
		Category category = categoryService.getCategoryById(video.getMcId(), video.getScId());
		UserDTO userDTO = null;
		try {
			ResultData<UserDTO> resp = userFeignApi.getUserByUid(uid);
			if (resp != null) {
				userDTO = resp.getData();
			}
		} catch (Exception ex) {
			log.warn("获取用户信息降级，uid={}", uid);
		}
		if (userDTO == null) {
			userDTO = new UserDTO();
		}
		Map<String, Object> map = new HashMap<>();
		map.put("video", video);
		map.put("stat", stat != null ? stat : new VideoStat());
		map.put("category", category != null ? category : new Category());
		map.put("user", userDTO);
		return ResultData.success(map, "获取置顶视频成功");
	}

	@Transactional
	@Override
	public ResultData<String> setPinnedVideo(Long uid, Long vid) {
		if (uid == null || uid <= 0 || vid == null || vid <= 0) {
			return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数无效");
		}
		Video video = videoMapper.selectById(vid);
		if (video == null) {
			return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST, "视频不存在");
		}
		if (!video.getUid().equals(uid)) {
			return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权限操作此视频");
		}
		if (video.getStatus() == null || video.getStatus() != 1) {
			return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "仅已过审视频可置顶");
		}
		new LambdaUpdateChainWrapper<>(videoMapper)
				.eq(Video::getUid, uid)
				.set(Video::getIsTop, (byte) 0)
				.update();
		boolean updated = new LambdaUpdateChainWrapper<>(videoMapper)
				.eq(Video::getVid, vid)
				.set(Video::getIsTop, (byte) 1)
				.update();
		return updated ? ResultData.success("设置置顶成功") : ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "设置置顶失败");
	}

	@Transactional
	@Override
	public ResultData<String> cancelPinnedVideo(Long uid, Long vid) {
		if (uid == null || uid <= 0 || vid == null || vid <= 0) {
			return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数无效");
		}
		Video video = videoMapper.selectById(vid);
		if (video == null) {
			return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST, "视频不存在");
		}
		if (!video.getUid().equals(uid)) {
			return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权限操作此视频");
		}
		boolean updated = new LambdaUpdateChainWrapper<>(videoMapper)
				.eq(Video::getVid, vid)
				.set(Video::getIsTop, (byte) 0)
				.update();
		return updated ? ResultData.success("取消置顶成功") : ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "取消置顶失败");
	}

}