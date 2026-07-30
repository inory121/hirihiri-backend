package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hiiro.entity.*;
import com.hiiro.entity.dto.*;
import com.hiiro.mapper.MessageNoticeMapper;
import com.hiiro.mapper.MessagePrivateMapper;
import com.hiiro.mapper.MessageSessionMapper;
import com.hiiro.service.FollowService;
import com.hiiro.service.MessageService;
import com.hiiro.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private MessageSessionMapper messageSessionMapper;

    @Resource
    private MessagePrivateMapper messagePrivateMapper;

    @Resource
    private MessageNoticeMapper messageNoticeMapper;

    @Resource
    private UserService userService;

    @Resource
    private FollowService followService;

    @Resource
    private MessageSocketBroker messageSocketBroker;

    @Override
    public ResultData<MessageUnreadDTO> getUnreadSummary(Long uid) {
        return ResultData.success(buildUnreadSummary(uid));
    }

    @Override
    public ResultData<List<MessageSessionDTO>> getSessions(Long uid, boolean strangerMode) {
        List<MessageSession> sessions = messageSessionMapper.selectList(
                new LambdaQueryWrapper<MessageSession>()
                        .and(wrapper -> wrapper.eq(MessageSession::getUidLow, uid)
                                .or()
                                .eq(MessageSession::getUidHigh, uid))
                        .orderByDesc(MessageSession::getLastMessageTime)
                        .orderByDesc(MessageSession::getUpdateTime)
        );

        if (sessions.isEmpty()) {
            return ResultData.success(Collections.emptyList());
        }

        List<Long> peerUids = sessions.stream().map(session -> getPeerUid(session, uid)).distinct().toList();
        Map<Long, UserDTO> userMap = buildUserMap(peerUids);
        Set<Long> followingUids = new HashSet<>(followService.getFollowingUids(uid));

        List<MessageSessionDTO> result = new ArrayList<>();
        for (MessageSession session : sessions) {
            Long peerUid = getPeerUid(session, uid);
            boolean following = followingUids.contains(peerUid);
            if (following == strangerMode) {
                continue;
            }
            MessageSessionDTO dto = toSessionDTO(session, uid, userMap.get(peerUid), following);
            result.add(dto);
        }
        return ResultData.success(result);
    }

    @Override
    @Transactional
    public ResultData<MessageSessionDTO> createOrGetSession(Long uid, Long targetUid) {
        if (Objects.equals(uid, targetUid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "不能给自己发消息");
        }
        UserDTO targetUser = userService.getUserByUid(targetUid);
        if (targetUser == null) {
            return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "目标用户不存在");
        }

        MessageSession session = getOrCreateSession(uid, targetUid);
        boolean following = followService.isFollowing(uid, targetUid);
        return ResultData.success(toSessionDTO(session, uid, targetUser, following));
    }

    @Override
    public ResultData<List<MessagePrivateDTO>> getSessionMessages(Long uid, Long sessionId, Integer pageNum, Integer pageSize) {
        MessageSession session = messageSessionMapper.selectById(sessionId);
        if (session == null || !containsUser(session, uid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "会话不存在");
        }

        int currentPage = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 30 : Math.min(pageSize, 100);
        int offset = (currentPage - 1) * size;

        List<MessagePrivate> messages = messagePrivateMapper.selectList(
                new LambdaQueryWrapper<MessagePrivate>()
                        .eq(MessagePrivate::getSessionId, sessionId)
                        .orderByDesc(MessagePrivate::getCreateTime)
                        .last("LIMIT " + offset + ", " + size)
        );
        Collections.reverse(messages);
        return ResultData.success(buildPrivateDTOs(messages));
    }

    @Override
    @Transactional
    public ResultData<MessagePrivateDTO> sendPrivateMessage(Long uid, MessageSendDTO dto) {
        if (dto == null || dto.getTargetUid() == null || !StringUtils.hasText(dto.getContent())) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "消息参数不完整");
        }
        String content = dto.getContent().trim();
        if (content.isEmpty() || content.length() > 1000) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "消息长度需在1-1000字符之间");
        }
        if (Objects.equals(uid, dto.getTargetUid())) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "不能给自己发消息");
        }

        UserDTO targetUser = userService.getUserByUid(dto.getTargetUid());
        if (targetUser == null) {
            return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "目标用户不存在");
        }

        MessageSession session = getOrCreateSession(uid, dto.getTargetUid());
        MessagePrivate message = new MessagePrivate();
        message.setSessionId(session.getId());
        message.setSenderUid(uid);
        message.setReceiverUid(dto.getTargetUid());
        message.setContent(content);
        message.setContentType("text");
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.now());
        messagePrivateMapper.insert(message);

        touchSessionAfterSend(session, uid, content);

        MessagePrivateDTO messageDTO = buildPrivateDTO(message);
        pushPrivateMessageUpdates(uid, dto.getTargetUid(), session.getId(), messageDTO);
        return ResultData.success(messageDTO, "发送成功");
    }

    @Override
    @Transactional
    public ResultData<String> markSessionRead(Long uid, Long sessionId) {
        MessageSession session = messageSessionMapper.selectById(sessionId);
        if (session == null || !containsUser(session, uid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "会话不存在");
        }

        boolean isLow = Objects.equals(session.getUidLow(), uid);
        int currentUnread = isLow ? safeInt(session.getUnreadLow()) : safeInt(session.getUnreadHigh());
        if (currentUnread <= 0) {
            return ResultData.success("已读");
        }

        LambdaUpdateWrapper<MessageSession> updateWrapper = new LambdaUpdateWrapper<MessageSession>()
                .eq(MessageSession::getId, sessionId);
        if (isLow) {
            updateWrapper.set(MessageSession::getUnreadLow, 0);
            session.setUnreadLow(0);
        } else {
            updateWrapper.set(MessageSession::getUnreadHigh, 0);
            session.setUnreadHigh(0);
        }
        messageSessionMapper.update(null, updateWrapper);

        messagePrivateMapper.update(
                null,
                new LambdaUpdateWrapper<MessagePrivate>()
                        .eq(MessagePrivate::getSessionId, sessionId)
                        .eq(MessagePrivate::getReceiverUid, uid)
                        .eq(MessagePrivate::getIsRead, 0)
                        .set(MessagePrivate::getIsRead, 1)
                        .set(MessagePrivate::getReadTime, LocalDateTime.now())
        );

        messageSocketBroker.sendToUser(uid, "UNREAD_UPDATED", buildUnreadSummary(uid));
        return ResultData.success("已读");
    }

    @Override
    public ResultData<List<MessageNoticeDTO>> getNotices(Long uid, String noticeType, Integer pageNum, Integer pageSize) {
        int currentPage = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 100);
        int offset = (currentPage - 1) * size;

        LambdaQueryWrapper<MessageNotice> wrapper = new LambdaQueryWrapper<MessageNotice>()
                .eq(MessageNotice::getReceiveUid, uid)
                .orderByDesc(MessageNotice::getCreateTime)
                .last("LIMIT " + offset + ", " + size);
        if (StringUtils.hasText(noticeType)) {
            wrapper.eq(MessageNotice::getNoticeType, noticeType);
        }

        List<MessageNotice> notices = messageNoticeMapper.selectList(wrapper);
        return ResultData.success(buildNoticeDTOs(notices));
    }

    @Override
    @Transactional
    public ResultData<String> markNoticeRead(Long uid, Long noticeId) {
        MessageNotice notice = messageNoticeMapper.selectById(noticeId);
        if (notice == null || !Objects.equals(notice.getReceiveUid(), uid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "通知不存在");
        }
        if (Objects.equals(notice.getIsRead(), 1)) {
            return ResultData.success("已读");
        }
        messageNoticeMapper.update(
                null,
                new LambdaUpdateWrapper<MessageNotice>()
                        .eq(MessageNotice::getId, noticeId)
                        .set(MessageNotice::getIsRead, 1)
        );
        messageSocketBroker.sendToUser(uid, "UNREAD_UPDATED", buildUnreadSummary(uid));
        return ResultData.success("已读");
    }

    @Override
    @Transactional
    public ResultData<String> markAllNoticesRead(Long uid, String noticeType) {
        LambdaUpdateWrapper<MessageNotice> updateWrapper = new LambdaUpdateWrapper<MessageNotice>()
                .eq(MessageNotice::getReceiveUid, uid)
                .eq(MessageNotice::getIsRead, 0)
                .set(MessageNotice::getIsRead, 1);
        if (StringUtils.hasText(noticeType)) {
            updateWrapper.eq(MessageNotice::getNoticeType, noticeType);
        }
        messageNoticeMapper.update(null, updateWrapper);
        messageSocketBroker.sendToUser(uid, "UNREAD_UPDATED", buildUnreadSummary(uid));
        return ResultData.success("全部已读");
    }

    @Override
    @Transactional
    public ResultData<String> deleteNotice(Long uid, Long noticeId) {
        if (noticeId == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "通知ID不能为空");
        }
        // 仅允许删除本人接收的通知，防止越权删除他人通知
        LambdaQueryWrapper<MessageNotice> queryWrapper = new LambdaQueryWrapper<MessageNotice>()
                .eq(MessageNotice::getId, noticeId)
                .eq(MessageNotice::getReceiveUid, uid);
        if (messageNoticeMapper.selectCount(queryWrapper) == 0) {
            return ResultData.fail(ResultCodeEnum.NOT_FOUND, "通知不存在");
        }
        messageNoticeMapper.deleteById(noticeId);
        messageSocketBroker.sendToUser(uid, "UNREAD_UPDATED", buildUnreadSummary(uid));
        return ResultData.success("删除成功");
    }

    @Override
    @Transactional
    public ResultData<Long> createInternalNotice(MessageNoticeCreateDTO dto) {
        if (dto == null || dto.getReceiveUid() == null || !StringUtils.hasText(dto.getNoticeType())) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "通知参数不完整");
        }
        if (dto.getActorUid() != null && Objects.equals(dto.getActorUid(), dto.getReceiveUid())) {
            return ResultData.success(0L, "跳过本人通知");
        }

        MessageNotice notice = new MessageNotice();
        notice.setReceiveUid(dto.getReceiveUid());
        notice.setActorUid(dto.getActorUid());
        notice.setNoticeType(dto.getNoticeType().trim());
        notice.setBizType(dto.getBizType());
        notice.setBizId(dto.getBizId());
        notice.setTitle(StringUtils.hasText(dto.getTitle()) ? dto.getTitle().trim() : buildNoticeTitle(dto.getNoticeType()));
        notice.setContentSummary(limitText(dto.getContentSummary(), 500));
        notice.setIsRead(0);
        notice.setExtJson(dto.getExtJson());
        notice.setCreateTime(LocalDateTime.now());
        messageNoticeMapper.insert(notice);

        MessageNoticeDTO noticeDTO = buildNoticeDTO(notice, buildUserMap(notice.getActorUid() == null ? List.of() : List.of(notice.getActorUid())));
        messageSocketBroker.sendToUser(notice.getReceiveUid(), "NOTICE_UPDATED", noticeDTO);
        messageSocketBroker.sendToUser(notice.getReceiveUid(), "UNREAD_UPDATED", buildUnreadSummary(notice.getReceiveUid()));
        return ResultData.success(notice.getId(), "创建通知成功");
    }

    @Override
    @Transactional
    public ResultData<String> deleteInternalNotice(Long receiveUid, Long actorUid, String noticeType, String bizType, Long bizId) {
        if (receiveUid == null || !StringUtils.hasText(noticeType)
                || !StringUtils.hasText(bizType) || bizId == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "通知删除参数不完整");
        }
        LambdaQueryWrapper<MessageNotice> wrapper = new LambdaQueryWrapper<MessageNotice>()
                .eq(MessageNotice::getReceiveUid, receiveUid)
                .eq(MessageNotice::getNoticeType, noticeType.trim())
                .eq(MessageNotice::getBizType, bizType.trim())
                .eq(MessageNotice::getBizId, bizId);
        if (actorUid != null) {
            wrapper.eq(MessageNotice::getActorUid, actorUid);
        }
        int deleted = messageNoticeMapper.delete(wrapper);
        if (deleted > 0) {
            messageSocketBroker.sendToUser(receiveUid, "UNREAD_UPDATED", buildUnreadSummary(receiveUid));
        }
        return ResultData.success("删除成功");
    }

    @Override
    public ResultData<String> deleteNoticeByBizIds(List<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            return ResultData.success("无需删除");
        }
        // 仅清理评论相关的通知类型，避免 bizId 数值与其它业务重合时误删
        List<String> commentTypes = Arrays.asList("reply", "at", "like");
        // 收集受影响的接收者，用于推送未读更新
        List<Long> receiveUids = messageNoticeMapper.selectList(
                        new LambdaQueryWrapper<MessageNotice>()
                                .select(MessageNotice::getReceiveUid)
                                .in(MessageNotice::getBizId, bizIds)
                                .in(MessageNotice::getNoticeType, commentTypes))
                .stream()
                .map(MessageNotice::getReceiveUid)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        messageNoticeMapper.delete(
                new LambdaQueryWrapper<MessageNotice>()
                        .in(MessageNotice::getBizId, bizIds)
                        .in(MessageNotice::getNoticeType, commentTypes));

        for (Long uid : receiveUids) {
            messageSocketBroker.sendToUser(uid, "UNREAD_UPDATED", buildUnreadSummary(uid));
        }
        return ResultData.success("删除成功");
    }

    @Override
    @Transactional
    public ResultData<String> deleteSession(Long uid, Long sessionId) {
        MessageSession session = messageSessionMapper.selectById(sessionId);
        if (session == null || !containsUser(session, uid)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "会话不存在");
        }

        // 删除会话的所有消息
        messagePrivateMapper.delete(
                new LambdaQueryWrapper<MessagePrivate>()
                        .eq(MessagePrivate::getSessionId, sessionId)
        );

        // 删除会话
        messageSessionMapper.deleteById(sessionId);

        // 推送未读更新
        messageSocketBroker.sendToUser(uid, "UNREAD_UPDATED", buildUnreadSummary(uid));

        Long peerUid = getPeerUid(session, uid);
        if (peerUid != null) {
            messageSocketBroker.sendToUser(peerUid, "UNREAD_UPDATED", buildUnreadSummary(peerUid));
        }

        return ResultData.success("删除成功");
    }

    private MessageSession getOrCreateSession(Long uid, Long targetUid) {
        Long uidLow = Math.min(uid, targetUid);
        Long uidHigh = Math.max(uid, targetUid);

        MessageSession session = messageSessionMapper.selectOne(
                new LambdaQueryWrapper<MessageSession>()
                        .eq(MessageSession::getUidLow, uidLow)
                        .eq(MessageSession::getUidHigh, uidHigh)
                        .last("LIMIT 1")
        );
        if (session != null) {
            return session;
        }

        MessageSession newSession = new MessageSession();
        newSession.setUidLow(uidLow);
        newSession.setUidHigh(uidHigh);
        newSession.setUnreadLow(0);
        newSession.setUnreadHigh(0);
        newSession.setCreateTime(LocalDateTime.now());
        newSession.setUpdateTime(LocalDateTime.now());
        try {
            messageSessionMapper.insert(newSession);
            return newSession;
        } catch (DuplicateKeyException e) {
            return messageSessionMapper.selectOne(
                    new LambdaQueryWrapper<MessageSession>()
                            .eq(MessageSession::getUidLow, uidLow)
                            .eq(MessageSession::getUidHigh, uidHigh)
                            .last("LIMIT 1")
            );
        }
    }

    private void touchSessionAfterSend(MessageSession session, Long senderUid, String content) {
        boolean senderIsLow = Objects.equals(session.getUidLow(), senderUid);
        int nextUnreadLow = safeInt(session.getUnreadLow());
        int nextUnreadHigh = safeInt(session.getUnreadHigh());
        if (senderIsLow) {
            nextUnreadHigh += 1;
        } else {
            nextUnreadLow += 1;
        }

        String summary = limitText(content, 80);
        session.setLastMessage(summary);
        session.setLastMessageTime(LocalDateTime.now());
        session.setUnreadLow(nextUnreadLow);
        session.setUnreadHigh(nextUnreadHigh);
        session.setUpdateTime(LocalDateTime.now());
        messageSessionMapper.updateById(session);
    }

    private void pushPrivateMessageUpdates(Long senderUid, Long receiverUid, Long sessionId, MessagePrivateDTO messageDTO) {
        MessageSession senderSession = messageSessionMapper.selectById(sessionId);
        UserDTO senderPeer = userService.getUserByUid(receiverUid);
        UserDTO receiverPeer = userService.getUserByUid(senderUid);

        if (senderSession != null && senderPeer != null) {
            messageSocketBroker.sendToUser(
                    senderUid,
                    "SESSION_UPDATED",
                    toSessionDTO(senderSession, senderUid, senderPeer, followService.isFollowing(senderUid, receiverUid))
            );
        }
        if (senderSession != null && receiverPeer != null) {
            messageSocketBroker.sendToUser(
                    receiverUid,
                    "SESSION_UPDATED",
                    toSessionDTO(senderSession, receiverUid, receiverPeer, followService.isFollowing(receiverUid, senderUid))
            );
        }
        messageSocketBroker.sendToUser(senderUid, "PRIVATE_MESSAGE", messageDTO);
        messageSocketBroker.sendToUser(receiverUid, "PRIVATE_MESSAGE", messageDTO);
        messageSocketBroker.sendToUser(senderUid, "UNREAD_UPDATED", buildUnreadSummary(senderUid));
        messageSocketBroker.sendToUser(receiverUid, "UNREAD_UPDATED", buildUnreadSummary(receiverUid));
    }

    private MessageUnreadDTO buildUnreadSummary(Long uid) {
        MessageUnreadDTO dto = new MessageUnreadDTO();
        List<MessageSession> sessions = messageSessionMapper.selectList(
                new LambdaQueryWrapper<MessageSession>()
                        .and(wrapper -> wrapper.eq(MessageSession::getUidLow, uid)
                                .or()
                                .eq(MessageSession::getUidHigh, uid))
        );
        Set<Long> followingUids = new HashSet<>(followService.getFollowingUids(uid));
        int privateUnread = 0;
        int strangerUnread = 0;
        for (MessageSession session : sessions) {
            int unread = Objects.equals(session.getUidLow(), uid) ? safeInt(session.getUnreadLow()) : safeInt(session.getUnreadHigh());
            if (unread <= 0) {
                continue;
            }
            Long peerUid = getPeerUid(session, uid);
            if (followingUids.contains(peerUid)) {
                privateUnread += unread;
            } else {
                strangerUnread += unread;
            }
        }

        List<MessageNotice> unreadNotices = messageNoticeMapper.selectList(
                new LambdaQueryWrapper<MessageNotice>()
                        .eq(MessageNotice::getReceiveUid, uid)
                        .eq(MessageNotice::getIsRead, 0)
        );

        int replyUnread = 0;
        int atUnread = 0;
        int likeUnread = 0;
        int systemUnread = 0;
        for (MessageNotice notice : unreadNotices) {
            switch (String.valueOf(notice.getNoticeType())) {
                case "reply" -> replyUnread++;
                case "at" -> atUnread++;
                case "like" -> likeUnread++;
                case "system" -> systemUnread++;
                default -> systemUnread++;
            }
        }

        dto.setPrivateUnread(privateUnread);
        dto.setStrangerUnread(strangerUnread);
        dto.setReplyUnread(replyUnread);
        dto.setAtUnread(atUnread);
        dto.setLikeUnread(likeUnread);
        dto.setSystemUnread(systemUnread);
        dto.setTotalUnread(privateUnread + strangerUnread + replyUnread + atUnread + likeUnread + systemUnread);
        return dto;
    }

    private List<MessagePrivateDTO> buildPrivateDTOs(List<MessagePrivate> messages) {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> senderIds = messages.stream().map(MessagePrivate::getSenderUid).distinct().toList();
        Map<Long, UserDTO> userMap = buildUserMap(senderIds);
        return messages.stream().map(message -> buildPrivateDTO(message, userMap)).toList();
    }

    private MessagePrivateDTO buildPrivateDTO(MessagePrivate message) {
        return buildPrivateDTO(message, buildUserMap(List.of(message.getSenderUid())));
    }

    private MessagePrivateDTO buildPrivateDTO(MessagePrivate message, Map<Long, UserDTO> userMap) {
        MessagePrivateDTO dto = new MessagePrivateDTO();
        dto.setId(message.getId());
        dto.setSessionId(message.getSessionId());
        dto.setSenderUid(message.getSenderUid());
        dto.setReceiverUid(message.getReceiverUid());
        dto.setContent(message.getContent());
        dto.setContentType(message.getContentType());
        dto.setIsRead(message.getIsRead());
        dto.setCreateTime(message.getCreateTime());
        dto.setReadTime(message.getReadTime());
        dto.setSenderUser(userMap.get(message.getSenderUid()));
        return dto;
    }

    private List<MessageNoticeDTO> buildNoticeDTOs(List<MessageNotice> notices) {
        if (notices.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> actorUids = notices.stream()
                .map(MessageNotice::getActorUid)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserDTO> userMap = buildUserMap(actorUids);
        return notices.stream().map(notice -> buildNoticeDTO(notice, userMap)).toList();
    }

    private MessageNoticeDTO buildNoticeDTO(MessageNotice notice, Map<Long, UserDTO> userMap) {
        MessageNoticeDTO dto = new MessageNoticeDTO();
        dto.setId(notice.getId());
        dto.setReceiveUid(notice.getReceiveUid());
        dto.setNoticeType(notice.getNoticeType());
        dto.setBizType(notice.getBizType());
        dto.setBizId(notice.getBizId());
        dto.setTitle(notice.getTitle());
        dto.setContentSummary(notice.getContentSummary());
        dto.setIsRead(notice.getIsRead());
        dto.setExtJson(notice.getExtJson());
        dto.setCreateTime(notice.getCreateTime());
        dto.setActorUser(userMap.get(notice.getActorUid()));
        return dto;
    }

    private MessageSessionDTO toSessionDTO(MessageSession session, Long currentUid, UserDTO peerUser, boolean following) {
        MessageSessionDTO dto = new MessageSessionDTO();
        dto.setSessionId(session.getId());
        dto.setPeerUser(peerUser);
        dto.setLastMessage(session.getLastMessage());
        dto.setLastMessageTime(session.getLastMessageTime());
        dto.setUnreadCount(Objects.equals(session.getUidLow(), currentUid) ? safeInt(session.getUnreadLow()) : safeInt(session.getUnreadHigh()));
        dto.setFollowing(following);
        return dto;
    }

    private Map<Long, UserDTO> buildUserMap(List<Long> uids) {
        List<Long> uniqueIds = uids.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserDTO> users = userService.getBatchUserInfo(uniqueIds);
        if (users == null || users.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, UserDTO> userMap = new LinkedHashMap<>();
        for (UserDTO user : users) {
            userMap.put(user.getUid(), user);
        }
        return userMap;
    }

    private boolean containsUser(MessageSession session, Long uid) {
        return Objects.equals(session.getUidLow(), uid) || Objects.equals(session.getUidHigh(), uid);
    }

    private Long getPeerUid(MessageSession session, Long uid) {
        return Objects.equals(session.getUidLow(), uid) ? session.getUidHigh() : session.getUidLow();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String limitText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String buildNoticeTitle(String noticeType) {
        return switch (noticeType) {
            case "reply" -> "回复我的";
            case "at" -> "@我的";
            case "like" -> "收到的赞";
            case "system" -> "系统通知";
            default -> "消息通知";
        };
    }
}
