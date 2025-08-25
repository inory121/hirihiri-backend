package com.hiiro.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hiiro.entity.Category;
import com.hiiro.entity.VideoStat;
import com.hiiro.entity.dto.UserDTO;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineConfig {

	@Bean
	public Cache<Pair<String, String>, Category> categoryCache() {
		return Caffeine.newBuilder()
				.maximumSize(100_000)
				.expireAfterWrite(Duration.ofMinutes(10))
				.build();
	}

	@Bean
	public Cache<Long, UserDTO> userCache() {
		return Caffeine.newBuilder()
				.maximumSize(200_000)
				.expireAfterWrite(Duration.ofMinutes(5))
				.build();
	}

	@Bean
	public Cache<Long, VideoStat> videoStatCache() {
		return Caffeine.newBuilder()
				.maximumSize(200_000)
				.expireAfterWrite(Duration.ofMinutes(2))
				.build();
	}
} 