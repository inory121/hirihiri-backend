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

    /**
     * 增加/减少用户硬币
     *
     * @param uid    用户id
     * @param amount 变化数量（正数增加，负数减少）
     * @return {@link ResultData}<{@link String}>
     */
    @PostMapping("/api/user/coin/add")
    ResultData<String> addCoin(@RequestParam("uid") Long uid, @RequestParam("amount") Double amount);

    /**
     * 增加投币经验值（每日上限50）
     *
     * @param uid           用户id
     * @param requestedGain 请求增加的经验值
     * @return {@link ResultData}<{@link Integer}> 实际增加量
     */
    @PostMapping("/api/user/coin/exp/add")
    ResultData<Integer> addCoinExp(@RequestParam("uid") Long uid, @RequestParam("requestedGain") Integer requestedGain);

    /**
     * 增加经验值（按来源类型每日幂等，每天每类只发一次）
     *
     * @param uid    用户id
     * @param type   经验来源类型：login / watch / vip_watch / share / coin
     * @param amount 本次发放经验值
     * @return {@link ResultData}<{@link Integer}> 实际增加量（已发过则返回0）
     */
    @PostMapping("/api/user/exp/add")
    ResultData<Integer> addExp(@RequestParam("uid") Long uid,
                               @RequestParam("type") String type,
                               @RequestParam("amount") Integer amount);

}
