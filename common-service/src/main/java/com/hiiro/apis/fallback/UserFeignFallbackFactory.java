package com.hiiro.apis.fallback;

import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.MessageNoticeCreateDTO;
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

            @Override
            public ResultData<UserDTO> getUserByUsername(String username) {
                return ResultData.fail(ResultCodeEnum.TOO_MANY_REQUESTS, "用户服务不可用");
            }

            @Override
            public ResultData<List<Long>> getFollowingUids(Long uid) {
                return ResultData.success(Collections.emptyList());
            }

            @Override
            public ResultData<Long> createInternalNotice(MessageNoticeCreateDTO dto) {
                return ResultData.fail(ResultCodeEnum.TOO_MANY_REQUESTS, "消息服务不可用");
            }

            @Override
            public ResultData<String> deleteInternalNotice(Long receiveUid, Long actorUid, String noticeType, String bizType, Long bizId) {
                return ResultData.fail(ResultCodeEnum.TOO_MANY_REQUESTS, "消息服务不可用");
            }

            @Override
            public ResultData<String> deleteNoticeByBizIds(List<Long> bizIds) {
                return ResultData.fail(ResultCodeEnum.TOO_MANY_REQUESTS, "消息服务不可用");
            }
        };
    }
}
