package com.hiiro.apis;

import com.hiiro.apis.fallback.UserFeignFallbackFactory;
import com.hiiro.config.FeignConfig;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.MessageNoticeCreateDTO;
import com.hiiro.entity.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/api/user/by-username/{username}")
    ResultData<UserDTO> getUserByUsername(@PathVariable("username") String username);

    @GetMapping("/api/follow/following-uids/{uid}")
    ResultData<List<Long>> getFollowingUids(@PathVariable("uid") Long uid);

    @PostMapping("/api/message/internal/notice")
    ResultData<Long> createInternalNotice(@RequestBody MessageNoticeCreateDTO dto);

    @PostMapping("/api/message/internal/notice/delete")
    ResultData<String> deleteInternalNotice(
            @RequestParam("receiveUid") Long receiveUid,
            @RequestParam(value = "actorUid", required = false) Long actorUid,
            @RequestParam("noticeType") String noticeType,
            @RequestParam("bizType") String bizType,
            @RequestParam("bizId") Long bizId);

    @PostMapping("/api/message/internal/notice/delete-by-biz-ids")
    ResultData<String> deleteNoticeByBizIds(@RequestBody List<Long> bizIds);

}
