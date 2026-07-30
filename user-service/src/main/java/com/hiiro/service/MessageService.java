package com.hiiro.service;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.*;

import java.util.List;

public interface MessageService {
    ResultData<MessageUnreadDTO> getUnreadSummary(Long uid);

    ResultData<List<MessageSessionDTO>> getSessions(Long uid, boolean strangerMode);

    ResultData<MessageSessionDTO> createOrGetSession(Long uid, Long targetUid);

    ResultData<List<MessagePrivateDTO>> getSessionMessages(Long uid, Long sessionId, Integer pageNum, Integer pageSize);

    ResultData<MessagePrivateDTO> sendPrivateMessage(Long uid, MessageSendDTO dto);

    ResultData<String> markSessionRead(Long uid, Long sessionId);

    ResultData<List<MessageNoticeDTO>> getNotices(Long uid, String noticeType, Integer pageNum, Integer pageSize);

    ResultData<String> markNoticeRead(Long uid, Long noticeId);

    ResultData<String> markAllNoticesRead(Long uid, String noticeType);

    ResultData<String> deleteNotice(Long uid, Long noticeId);

    ResultData<Long> createInternalNotice(MessageNoticeCreateDTO dto);

    /**
     * 按条件删除内部通知（用于取消点赞/收藏等互动时撤回对应通知）
     *
     * @param receiveUid 接收者（必填）
     * @param actorUid   触发者（可空，传空则不限）
     * @param noticeType 通知类型（必填）
     * @param bizType    业务类型（必填）
     * @param bizId      业务ID（必填）
     */
    ResultData<String> deleteInternalNotice(Long receiveUid, Long actorUid, String noticeType, String bizType, Long bizId);

    /**
     * 评论删除时，按 bizId（评论id，可含级联子评论）批量删除关联的通知
     * （回复/@/点赞三类，bizId 均指向评论）
     *
     * @param bizIds 被删评论 id 列表
     */
    ResultData<String> deleteNoticeByBizIds(List<Long> bizIds);

    ResultData<String> deleteSession(Long uid, Long sessionId);
}
