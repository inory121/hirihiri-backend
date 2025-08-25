package com.hiiro.apis;

import com.hiiro.apis.fallback.UserFeignFallbackFactory;
import com.hiiro.config.FeignConfig;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "user-service",
        configuration = FeignConfig.class,
        fallbackFactory = UserFeignFallbackFactory.class
)
public interface UserFeignApi {

    @PostMapping("/api/user/batch/info")
    List<UserDTO> getBatchUserInfo(@RequestBody List<Long> uids);

    @GetMapping("/api/user/info/{uid}")
    ResultData<UserDTO> getUserByUid(@PathVariable("uid") Long uid); //一定要写参数名"uid"，否则openfeign会使用post请求

}
