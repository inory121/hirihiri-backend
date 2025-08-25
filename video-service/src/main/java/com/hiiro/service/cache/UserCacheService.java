package com.hiiro.service.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.dto.UserDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserCacheService {

	@Resource
	private Cache<Long, UserDTO> userCache;
	@Resource
	private UserFeignApi userFeignApi;

	public Map<Long, UserDTO> getBatch(List<Long> uids) {
		if (uids == null || uids.isEmpty()) return Collections.emptyMap();
		Map<Long, UserDTO> hit = new HashMap<>();
		List<Long> miss = new ArrayList<>();
		for (Long uid : uids) {
			UserDTO dto = userCache.getIfPresent(uid);
			if (dto != null) hit.put(uid, dto); else miss.add(uid);
		}
		if (!miss.isEmpty()) {
			List<UserDTO> fromRemote = userFeignApi.getBatchUserInfo(miss);
			for (UserDTO dto : fromRemote) {
				userCache.put(dto.getUid(), dto);
				hit.putIfAbsent(dto.getUid(), dto);
			}
		}
		return hit;
	}
} 