package com.hiiro.apis.fallback;

import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.UserDTO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class UserFeignFallbackFactory implements FallbackFactory<UserFeignApi> {
	@Override
	public UserFeignApi create(Throwable cause) {
		return new UserFeignApi() {
			@Override
			public List<UserDTO> getBatchUserInfo(List<Long> uids) {
				return Collections.emptyList();
			}

			@Override
			public ResultData<UserDTO> getUserByUid(Long uid) {
				return ResultData.fail(ResultCodeEnum.TOO_MANY_REQUESTS, "用户服务不可用");
			}
		};
	}
}
