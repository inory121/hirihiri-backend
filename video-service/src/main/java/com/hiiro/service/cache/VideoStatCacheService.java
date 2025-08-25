package com.hiiro.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.hiiro.entity.VideoStat;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VideoStatCacheService {

	@Resource
	private Cache<Long, VideoStat> videoStatCache;
	@Resource
	private VideoStatService videoStatService;

	public Map<Long, VideoStat> getBatch(List<Long> vids) {
		if (vids == null || vids.isEmpty()) return Collections.emptyMap();
		Map<Long, VideoStat> hit = new HashMap<>();
		List<Long> miss = new ArrayList<>();
		for (Long vid : vids) {
			VideoStat stat = videoStatCache.getIfPresent(vid);
			if (stat != null) hit.put(vid, stat); else miss.add(vid);
		}
		if (!miss.isEmpty()) {
			List<VideoStat> fromDb = videoStatService.listByIds(miss);
			for (VideoStat stat : fromDb) {
				videoStatCache.put(stat.getVid(), stat);
				hit.putIfAbsent(stat.getVid(), stat);
			}
			// 对缺失的也回写一个空壳，避免穿透
			for (Long vid : miss) {
				hit.putIfAbsent(vid, new VideoStat());
			}
		}
		return hit;
	}
} 