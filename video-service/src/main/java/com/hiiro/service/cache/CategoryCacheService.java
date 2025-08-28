package com.hiiro.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.hiiro.entity.Category;
import com.hiiro.service.CategoryService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CategoryCacheService {

	@Resource
	private Cache<Pair<String, String>, Category> categoryCache;
	@Resource
	private CategoryService categoryService;

	public Map<Pair<String, String>, Category> getBatch(List<Pair<String, String>> keys) {
		if (keys == null || keys.isEmpty()) return Collections.emptyMap();
		Map<Pair<String, String>, Category> hit = new HashMap<>();
		List<Pair<String, String>> miss = new ArrayList<>();
		for (Pair<String, String> k : keys) {
			Category c = categoryCache.getIfPresent(k);
			if (c != null) hit.put(k, c); else miss.add(k);
		}
		if (!miss.isEmpty()) {
			List<String> mcIds = miss.stream().map(Pair::getLeft).distinct().toList();
			List<String> scIds = miss.stream().map(Pair::getRight).distinct().toList();
			// 简化：一次 IN 查询，内存过滤
			List<Category> fromDb = categoryService.list()
					.stream()
					.filter(c -> mcIds.contains(c.getMcId()) && scIds.contains(c.getScId()))
					.toList();
			for (Category c : fromDb) {
				Pair<String, String> k = Pair.of(c.getMcId(), c.getScId());
				categoryCache.put(k, c);
				hit.putIfAbsent(k, c);
			}
		}
		return hit;
	}
} 