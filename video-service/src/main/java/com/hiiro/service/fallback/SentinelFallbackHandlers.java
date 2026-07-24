package com.hiiro.service.fallback;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SentinelFallbackHandlers {

	public static ResultData<Map<String, Object>> searchFallback(String keyword, Integer pageNum, Integer pageSize, Throwable t) {
		Map<String, Object> body = new HashMap<>(2);
		body.put("records", Collections.emptyList());
		body.put("total", 0);
		return ResultData.success(body, "视频搜索接口降级：请稍后重试");
	}

	public static ResultData<Map<String, Object>> searchBlocked(String keyword, Integer pageNum, Integer pageSize, BlockException e) {
		Map<String, Object> body = new HashMap<>(2);
		body.put("records", Collections.emptyList());
		body.put("total", 0);
		return ResultData.success(body, "视频搜索接口被限流");
	}

	public static ResultData<Map<String, Object>> listFallback(Integer pageNum, Integer pageSize, Throwable t) {
		Map<String, Object> body = new HashMap<>(2);
		body.put("records", Collections.emptyList());
		body.put("total", 0);
		return ResultData.success(body, "视频列表暂时不可用，返回空结果");
	}

	public static ResultData<Map<String, Object>> listBlocked(Integer pageNum, Integer pageSize, BlockException e) {
		Map<String, Object> body = new HashMap<>(2);
		body.put("records", Collections.emptyList());
		body.put("total", 0);
		return ResultData.success(body, "视频列表接口被限流");
	}
} 