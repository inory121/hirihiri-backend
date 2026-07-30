package com.hiiro.controller;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.*;
import com.hiiro.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息中心")
@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    @Operation(summary = "获取未读统计")
    @GetMapping("/unread")
    public ResultData<MessageUnreadDTO> getUnreadSummary(@RequestHeader("uid") String uid) {
        return messageService.getUnreadSummary(Long.parseLong(uid));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public ResultData<List<MessageSessionDTO>> getSessions(
            @RequestHeader("uid") String uid,
            @RequestParam(name = "stranger", defaultValue = "false") boolean stranger) {
        return messageService.getSessions(Long.parseLong(uid), stranger);
    }

    @Operation(summary = "创建或获取私信会话")
    @PostMapping("/session/private/{targetUid}")
    public ResultData<MessageSessionDTO> createOrGetSession(
            @RequestHeader("uid") String uid,
            @PathVariable("targetUid") Long targetUid) {
        return messageService.createOrGetSession(Long.parseLong(uid), targetUid);
    }

    @Operation(summary = "获取会话消息")
    @GetMapping("/session/{sessionId}/messages")
    public ResultData<List<MessagePrivateDTO>> getSessionMessages(
            @RequestHeader("uid") String uid,
            @PathVariable("sessionId") Long sessionId,
            @RequestParam(name = "pageNum", required = false) Integer pageNum,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return messageService.getSessionMessages(Long.parseLong(uid), sessionId, pageNum, pageSize);
    }

    @Operation(summary = "发送私信")
    @PostMapping("/private/send")
    public ResultData<MessagePrivateDTO> sendPrivateMessage(
            @RequestHeader("uid") String uid,
            @RequestBody MessageSendDTO dto) {
        return messageService.sendPrivateMessage(Long.parseLong(uid), dto);
    }

    @Operation(summary = "会话已读")
    @PostMapping("/session/{sessionId}/read")
    public ResultData<String> markSessionRead(
            @RequestHeader("uid") String uid,
            @PathVariable("sessionId") Long sessionId) {
        return messageService.markSessionRead(Long.parseLong(uid), sessionId);
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/session/{sessionId}/delete")
    public ResultData<String> deleteSession(
            @RequestHeader("uid") String uid,
            @PathVariable("sessionId") Long sessionId) {
        return messageService.deleteSession(Long.parseLong(uid), sessionId);
    }

    @Operation(summary = "获取通知列表")
    @GetMapping("/notices")
    public ResultData<List<MessageNoticeDTO>> getNotices(
            @RequestHeader("uid") String uid,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "pageNum", required = false) Integer pageNum,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return messageService.getNotices(Long.parseLong(uid), type, pageNum, pageSize);
    }

    @Operation(summary = "单条通知已读")
    @PostMapping("/notice/{noticeId}/read")
    public ResultData<String> markNoticeRead(
            @RequestHeader("uid") String uid,
            @PathVariable("noticeId") Long noticeId) {
        return messageService.markNoticeRead(Long.parseLong(uid), noticeId);
    }

    @Operation(summary = "全部通知已读")
    @PostMapping("/notice/read-all")
    public ResultData<String> markAllNoticesRead(
            @RequestHeader("uid") String uid,
            @RequestParam(name = "type", required = false) String type) {
        return messageService.markAllNoticesRead(Long.parseLong(uid), type);
    }

    @Operation(summary = "删除单条通知")
    @DeleteMapping("/notice/{noticeId}/delete")
    public ResultData<String> deleteNotice(
            @RequestHeader("uid") String uid,
            @PathVariable("noticeId") Long noticeId) {
        return messageService.deleteNotice(Long.parseLong(uid), noticeId);
    }

    @Operation(summary = "内部创建通知")
    @PostMapping("/internal/notice")
    @PreAuthorize("@accessControl.isInternalRequest() || hasAnyRole('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResultData<Long> createInternalNotice(@RequestBody MessageNoticeCreateDTO dto) {
        if (dto == null || dto.getReceiveUid() == null || !StringUtils.hasText(dto.getNoticeType())) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "通知参数不完整");
        }
        return messageService.createInternalNotice(dto);
    }

    @Operation(summary = "内部按条件删除通知（取消点赞/收藏时撤回）")
    @PostMapping("/internal/notice/delete")
    @PreAuthorize("@accessControl.isInternalRequest() || hasAnyRole('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResultData<String> deleteInternalNotice(
            @RequestParam("receiveUid") Long receiveUid,
            @RequestParam(value = "actorUid", required = false) Long actorUid,
            @RequestParam("noticeType") String noticeType,
            @RequestParam("bizType") String bizType,
            @RequestParam("bizId") Long bizId) {
        return messageService.deleteInternalNotice(receiveUid, actorUid, noticeType, bizType, bizId);
    }

    @PostMapping("/internal/notice/delete-by-biz-ids")
    @PreAuthorize("@accessControl.isInternalRequest() || hasAnyRole('ROLE_ADMIN','ROLE_SUPER_ADMIN')")
    public ResultData<String> deleteNoticeByBizIds(@RequestBody List<Long> bizIds) {
        return messageService.deleteNoticeByBizIds(bizIds);
    }
}
