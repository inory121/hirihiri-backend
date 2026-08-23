package com.hiiro.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.Dynamic;
import com.hiiro.entity.DynamicLike;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.entity.VideoStat;
import com.hiiro.entity.dto.DynamicDTO;
import com.hiiro.entity.dto.DynamicPublishDTO;
import com.hiiro.entity.dto.DynamicUpDTO;
import com.hiiro.entity.dto.MessageNoticeCreateDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.CommentMapper;
import com.hiiro.mapper.DynamicLikeMapper;
import com.hiiro.mapper.DynamicMapper;
import com.hiiro.mapper.VideoMapper;
import com.hiiro.service.DynamicService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * 动态表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
@Slf4j
@Service
public class DynamicServiceImpl extends ServiceImpl<DynamicMapper, Dynamic> implements DynamicService {

    /**
     * 提取正文中 @数字(uid) 形式的提及，遵循 B 站风格（不去区分邮箱地址）
     */
    private static final Pattern AT_UID_PATTERN = Pattern.compile("@(\\d+)");
    /**
     * 提取正文中 @用户名 形式的提及（与评论一致）
     */
    private static final Pattern AT_USERNAME_PATTERN = Pattern.compile("(?<![A-Za-z0-9])@([a-zA-Z_]\\w*)(?![.\\w@])");

    @Resource
    private UserFeignApi userFeignApi;

    @Resource
    private VideoMapper videoMapper;

    @Resource
    private DynamicMapper dynamicMapper;

    @Resource
    private DynamicLikeMapper dynamicLikeMapper;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private VideoStatService videoStatService;

    @Override
    public ResultData<String> publish(Long uid, DynamicPublishDTO dto) {
        if (dto == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "动态内容不能为空");
        }
        int type = dto.getType() != null ? dto.getType() : 0;
        boolean hasContent = StringUtils.hasText(dto.getContent());
        boolean hasImages = !CollectionUtils.isEmpty(dto.getImages());

        // 普通动态：正文 / 图片 两者至少有一个即可发布（标题是选填，单独不算有效内容）
        if (type == 0 && !hasContent && !hasImages) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "动态内容不能为空");
        }
        // 视频动态（分享 or 投稿）必须关联视频
        if (type >= 1 && type <= 2 && dto.getVid() == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "视频动态必须关联视频");
        }
        // 转发动态（type=3）必须关联 parentId，正文可选填
        if (type == 3) {
            if (dto.getParentId() == null || dto.getParentId() <= 0) {
                return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "转发动态必须指定被转发的动态");
            }
            Dynamic parent = this.getById(dto.getParentId());
            if (parent == null) {
                return ResultData.fail(ResultCodeEnum.NOT_FOUND, "被转发的动态不存在");
            }
            // 禁止转发自己的转发（防止循环嵌套过深，这里简单处理不禁止多级，允许）
        }

        Dynamic dynamic = new Dynamic();
        dynamic.setUid(uid);
        dynamic.setTitle(dto.getTitle());
        dynamic.setContent(dto.getContent());
        dynamic.setType((byte) type);
        dynamic.setVid(dto.getVid());
        dynamic.setParentId(dto.getParentId());
        dynamic.setIsTop((byte) 0);
        dynamic.setCreateTime(LocalDateTime.now());
        if (hasImages) {
            dynamic.setImages(JSON.toJSONString(dto.getImages()));
        }
        this.save(dynamic);

        // 发布动态时，正文里 @uid 提及也要触发"@我的"通知（与评论行为一致）
        notifyAtOnDynamicPublish(uid, dynamic.getId(), dto.getContent());

        return ResultData.success("发布成功");
    }

    /**
     * 动态正文中的 @uid 提及，给被 @ 的用户发送 at 通知（noticeType=at，bizType=dynamic）
     */
    private void notifyAtOnDynamicPublish(Long selfUid, Long dynamicId, String content) {
        if (selfUid == null || dynamicId == null || !StringUtils.hasText(content)) {
            return;
        }
        Set<Long> atUids = new HashSet<>();
        // @数字(uid) 形式
        Matcher uidMatcher = AT_UID_PATTERN.matcher(content);
        while (uidMatcher.find()) {
            try {
                long atUid = Long.parseLong(uidMatcher.group(1));
                if (atUid != selfUid) {
                    atUids.add(atUid);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        // @用户名 形式：通过用户服务反查 uid（与评论一致）
        Matcher nameMatcher = AT_USERNAME_PATTERN.matcher(content);
        while (nameMatcher.find()) {
            String username = nameMatcher.group(1);
            if (username == null || username.isEmpty()) {
                continue;
            }
            try {
                ResultData<UserDTO> userResp = userFeignApi.getUserByUsername(username);
                UserDTO u = userResp != null ? userResp.getData() : null;
                if (u != null && u.getUid() != null && !u.getUid().equals(selfUid)) {
                    atUids.add(u.getUid());
                }
            } catch (Exception e) {
                log.warn("动态解析 @用户名 失败 username={}: {}", username, e.getMessage());
            }
        }
        if (atUids.isEmpty()) {
            return;
        }
        for (Long atUid : atUids) {
            try {
                MessageNoticeCreateDTO notice = new MessageNoticeCreateDTO();
                notice.setReceiveUid(atUid);
                notice.setActorUid(selfUid);
                notice.setNoticeType("at");
                notice.setBizType("dynamic");
                notice.setBizId(dynamicId);
                notice.setContentSummary(content);
                userFeignApi.createInternalNotice(notice);
            } catch (Exception e) {
                log.warn("动态 @ 通知发送失败, dynamicId={}, atUid={}", dynamicId, atUid, e);
            }
        }
    }

    @Override
    public ResultData<Map<String, Object>> getDynamicList(Integer pageNum, Integer pageSize, Integer type, Long uid, Long currentUid) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1 || pageSize > 50) {
            pageSize = 10;
        }

        // 当前登录用户已关注的所有 uid（用于填充 isFollowing）
        Set<Long> myFollowingUids = Collections.emptySet();
        if (currentUid != null) {
            try {
                ResultData<List<Long>> followingResp = userFeignApi.getFollowingUids(currentUid);
                List<Long> followingUids = (followingResp != null && followingResp.getData() != null)
                        ? followingResp.getData() : Collections.emptyList();
                if (!CollectionUtils.isEmpty(followingUids)) {
                    myFollowingUids = new HashSet<>(followingUids);
                }
            } catch (Exception e) {
                log.warn("获取当前用户关注列表失败, uid={}", currentUid, e);
            }
        }

        LambdaQueryWrapper<Dynamic> wrapper = new LambdaQueryWrapper<>();
        if (type != null && type == 2) {
            // 视频投稿 tab：仅匹配投稿视频（type=2），不包含分享视频（type=1）
            wrapper.eq(Dynamic::getType, (byte) 2);
        }
        if (uid != null) {
            wrapper.eq(Dynamic::getUid, uid);
        }
        wrapper.orderByDesc(Dynamic::getIsTop).orderByDesc(Dynamic::getCreateTime);

        Page<Dynamic> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        List<Dynamic> records = page.getRecords();
        List<DynamicDTO> list = new ArrayList<>(records.size());
        if (!CollectionUtils.isEmpty(records)) {
            // 批量获取发布者用户信息
            List<Long> uids = records.stream().map(Dynamic::getUid).distinct().toList();
            Map<Long, UserDTO> userMap = new HashMap<>();
            try {
                List<UserDTO> users = userFeignApi.getBatchUserInfo(uids);
                if (!CollectionUtils.isEmpty(users)) {
                    userMap = users.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(UserDTO::getUid, Function.identity(), (a, b) -> a));
                }
            } catch (Exception e) {
                log.warn("批量获取动态发布者信息失败, uids={}", uids, e);
            }
            // 填充当前登录用户对动态发布者的关注状态
            fillFollowingStatus(userMap, myFollowingUids);

            // 批量获取视频动态关联的视频信息
            List<Long> vids = records.stream()
                    .filter(d -> d.getType() != null && d.getType() >= 1 && d.getVid() != null)
                    .map(Dynamic::getVid)
                    .distinct()
                    .toList();
            Map<Long, Video> videoMap = new HashMap<>();
            Map<Long, VideoStat> videoStatMap = new HashMap<>();
            if (!CollectionUtils.isEmpty(vids)) {
                try {
                    List<Video> videos = videoMapper.selectBatchIds(vids);
                    if (!CollectionUtils.isEmpty(videos)) {
                        videoMap = videos.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toMap(Video::getVid, Function.identity(), (a, b) -> a));
                    }
                } catch (Exception e) {
                    log.warn("批量获取动态关联视频失败, vids={}", vids, e);
                }
                for (Long vid : vids) {
                    try {
                        VideoStat stat = videoStatService.getVideoStatByVid(vid);
                        videoStatMap.put(vid, stat);
                    } catch (Exception e) {
                        log.warn("获取视频统计失败, vid={}", vid, e);
                    }
                }
            }

            Set<Long> videoKeys = videoMap.keySet();

            // 批量获取视频UP主的用户信息
            Set<Long> videoOwnerUids = videoMap.values().stream()
                    .map(Video::getUid)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, UserDTO> videoUserMap = new HashMap<>();
            if (!CollectionUtils.isEmpty(videoOwnerUids)) {
                try {
                    List<UserDTO> videoUsers = userFeignApi.getBatchUserInfo(videoOwnerUids.stream().toList());
                    if (!CollectionUtils.isEmpty(videoUsers)) {
                        videoUserMap = videoUsers.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toMap(UserDTO::getUid, Function.identity(), (a, b) -> a));
                    }
                } catch (Exception e) {
                    log.warn("批量获取视频UP主信息失败, uids={}", videoOwnerUids, e);
                }
            }
            // 填充当前登录用户对视频UP主的关注状态
            fillFollowingStatus(videoUserMap, myFollowingUids);

            // 批量获取转发动态关联的被转发原动态（递归到最原始动态）
            List<Long> parentIds = records.stream()
                    .filter(d -> d.getType() != null && d.getType() == 3 && d.getParentId() != null)
                    .map(Dynamic::getParentId)
                    .distinct()
                    .toList();
            Map<Long, DynamicDTO> parentDtoMap = new HashMap<>();
            if (!CollectionUtils.isEmpty(parentIds)) {
                try {
                    // 递归收集所有相关动态ID，避免多层转发只返回一层parent
                    Set<Long> allRelatedIds = new HashSet<>(parentIds);
                    Set<Long> currentIds = new HashSet<>(parentIds);
                    while (!currentIds.isEmpty()) {
                        List<Dynamic> currentDynamics = this.listByIds(currentIds);
                        Set<Long> nextIds = currentDynamics.stream()
                                .filter(d -> d.getType() != null && d.getType() == 3 && d.getParentId() != null)
                                .map(Dynamic::getParentId)
                                .filter(allRelatedIds::add)
                                .collect(Collectors.toSet());
                        currentIds = nextIds;
                    }
                    List<Dynamic> allRelatedDynamics = this.listByIds(allRelatedIds);
                    if (!CollectionUtils.isEmpty(allRelatedDynamics)) {
                        // 构建所有相关动态需要的发布者、视频信息
                        Map<Long, UserDTO> parentUserMap = new HashMap<>();
                        List<Long> parentUids = allRelatedDynamics.stream().map(Dynamic::getUid).distinct().toList();
                        try {
                            List<UserDTO> pUsers = userFeignApi.getBatchUserInfo(parentUids);
                            if (!CollectionUtils.isEmpty(pUsers)) {
                                parentUserMap = pUsers.stream()
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toMap(UserDTO::getUid, Function.identity(), (a, b) -> a));
                            }
                        } catch (Exception e) {
                            log.warn("批量获取parent动态发布者失败", e);
                        }
                        // 填充当前登录用户对parent动态发布者的关注状态
                        fillFollowingStatus(parentUserMap, myFollowingUids);
                        List<Long> parentVids = allRelatedDynamics.stream()
                                .filter(pd -> pd.getType() != null && pd.getType() >= 1 && pd.getVid() != null)
                                .map(Dynamic::getVid).distinct().toList();
                        Map<Long, Video> pVideoMap = new HashMap<>();
                        Map<Long, VideoStat> pStatMap = new HashMap<>();
                        Map<Long, UserDTO> pVideoUserMap = new HashMap<>();
                        if (!CollectionUtils.isEmpty(parentVids)) {
                            try {
                                List<Video> pvs = videoMapper.selectBatchIds(parentVids);
                                if (!CollectionUtils.isEmpty(pvs)) {
                                    pVideoMap = pvs.stream()
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.toMap(Video::getVid, Function.identity(), (a, b) -> a));
                                }
                            } catch (Exception e) {
                                log.warn("批量获取parent动态视频失败", e);
                            }
                            for (Long vid : parentVids) {
                                try {
                                    pStatMap.put(vid, videoStatService.getVideoStatByVid(vid));
                                } catch (Exception e) {
                                    log.warn("获取parent视频stat失败, vid={}", vid, e);
                                }
                            }
                            Set<Long> pVideoOwnerUids = pVideoMap.values().stream()
                                    .map(Video::getUid).filter(Objects::nonNull).collect(Collectors.toSet());
                            if (!CollectionUtils.isEmpty(pVideoOwnerUids)) {
                                try {
                                    List<UserDTO> pVu = userFeignApi.getBatchUserInfo(pVideoOwnerUids.stream().toList());
                                    if (!CollectionUtils.isEmpty(pVu)) {
                                        pVideoUserMap = pVu.stream()
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toMap(UserDTO::getUid, Function.identity(), (a, b) -> a));
                                    }
                                } catch (Exception e) {
                                    log.warn("批量获取parent动态视频UP主失败", e);
                                }
                            }
                        }
                        // 填充当前登录用户对parent动态视频UP主的关注状态
                        fillFollowingStatus(pVideoUserMap, myFollowingUids);
                        Map<Long, DynamicDTO> allDtoMap = new HashMap<>();
                        for (Dynamic pd : allRelatedDynamics) {
                            DynamicDTO pdDto = convertDynamicToDto(pd, parentUserMap, pVideoMap, pStatMap, pVideoUserMap);
                            allDtoMap.put(pd.getId(), pdDto);
                        }
                        // 递归填充parent链
                        for (DynamicDTO dto : allDtoMap.values()) {
                            fillParentChain(dto, allDtoMap);
                        }
                        for (Long pid : parentIds) {
                            parentDtoMap.put(pid, allDtoMap.get(pid));
                        }
                    }
                } catch (Exception e) {
                    log.warn("批量获取被转发原动态失败, parentIds={}", parentIds, e);
                }
            }

            // 批量回填点赞数(likeCount)与当前用户是否已赞(liked)
            Map<Long, Integer> likeCountMap = new HashMap<>();
            Set<Long> likedSet = new HashSet<>();
            Map<Long, Long> commentCountMap = new HashMap<>();
            Map<Long, Long> repostCountMap = new HashMap<>();
            if (!records.isEmpty()) {
                List<Long> dynamicIds = records.stream()
                        .map(Dynamic::getId)
                        .filter(Objects::nonNull)
                        .toList();
                try {
                    dynamicLikeMapper.selectList(
                                    new LambdaQueryWrapper<DynamicLike>()
                                            .select(DynamicLike::getDynamicId)
                                            .in(DynamicLike::getDynamicId, dynamicIds))
                            .forEach(dl -> {
                                Long did = dl.getDynamicId();
                                likeCountMap.merge(did, 1, Integer::sum);
                            });
                    if (currentUid != null) {
                        likedSet = dynamicLikeMapper.selectList(
                                        new LambdaQueryWrapper<DynamicLike>()
                                                .select(DynamicLike::getDynamicId)
                                                .eq(DynamicLike::getUid, currentUid)
                                                .in(DynamicLike::getDynamicId, dynamicIds))
                                .stream()
                                .map(DynamicLike::getDynamicId)
                                .collect(Collectors.toSet());
                    }
                    // 批量统计每个动态的评论数（根评论+回复，含删除软删过滤）
                    commentMapper.selectList(
                                    new LambdaQueryWrapper<com.hiiro.entity.Comment>()
                                            .select(com.hiiro.entity.Comment::getDynamicId)
                                            .in(com.hiiro.entity.Comment::getDynamicId, dynamicIds)
                                            .eq(com.hiiro.entity.Comment::getIsDeleted, 0))
                            .forEach(c -> {
                                Long did = c.getDynamicId();
                                if (did != null) {
                                    commentCountMap.merge(did, 1L, Long::sum);
                                }
                            });
                    // 批量统计每个动态的转发数（parent_id 指向该动态的转发动态数）
                    dynamicMapper.selectList(
                                    new LambdaQueryWrapper<Dynamic>()
                                            .select(Dynamic::getParentId)
                                            .in(Dynamic::getParentId, dynamicIds))
                            .forEach(dp -> {
                                Long pid = dp.getParentId();
                                if (pid != null) {
                                    repostCountMap.merge(pid, 1L, Long::sum);
                                }
                            });
                } catch (Exception e) {
                    log.warn("批量获取动态点赞/评论/转发信息失败, dynamicIds={}", dynamicIds, e);
                }
            }

            for (Dynamic d : records) {
                DynamicDTO dto = convertDynamicToDto(d, userMap, videoMap, videoStatMap, videoUserMap);
                // 填充被转发的原动态
                dto.setParentId(d.getParentId());
                if (d.getParentId() != null) {
                    dto.setParent(parentDtoMap.get(d.getParentId()));
                }
                // 回填点赞信息、评论数与转发数
                Long did = d.getId();
                dto.setLikeCount(likeCountMap.getOrDefault(did, 0));
                dto.setLiked(likedSet.contains(did));
                dto.setCommentCount(commentCountMap.getOrDefault(did, 0L));
                dto.setRepostCount(repostCountMap.getOrDefault(did, 0L));
                list.add(dto);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", page.getTotal());
        return ResultData.success(result);
    }

    @Override
    public ResultData<Map<String, Object>> toggleLike(Long dynamicId, Long uid) {
        if (dynamicId == null || uid == null) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "参数缺失");
        }
        Dynamic dynamic = dynamicMapper.selectById(dynamicId);
        if (dynamic == null) {
            return ResultData.fail(ResultCodeEnum.NOT_FOUND, "动态不存在");
        }
        // 校验当前用户是否点赞过
        DynamicLike existing = dynamicLikeMapper.selectOne(
                new LambdaQueryWrapper<DynamicLike>()
                        .eq(DynamicLike::getDynamicId, dynamicId)
                        .eq(DynamicLike::getUid, uid)
                        .last("LIMIT 1"));
        Map<String, Object> data = new HashMap<>();
        if (existing != null) {
            // 取消点赞
            dynamicLikeMapper.deleteById(existing.getId());
            data.put("liked", false);
        } else {
            // 新增点赞
            DynamicLike like = new DynamicLike();
            like.setUid(uid);
            like.setDynamicId(dynamicId);
            like.setCreateTime(LocalDateTime.now());
            dynamicLikeMapper.insert(like);
            data.put("liked", true);
        }
        // 重新统计点赞数
        Long count = dynamicLikeMapper.selectCount(
                new LambdaQueryWrapper<DynamicLike>().eq(DynamicLike::getDynamicId, dynamicId));
        data.put("likeCount", count == null ? 0 : count.intValue());
        return ResultData.success(data);
    }

    @Override
    public ResultData<Map<String, Object>> getUpList(Integer pageNum, Integer pageSize, Long currentUid) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1 || pageSize > 500) {
            pageSize = 10;
        }

        // 当前用户已关注的 UP 主集合（未登录或取不到则只返回空列表）
        Set<Long> followingUids = Collections.emptySet();
        if (currentUid != null) {
            try {
                ResultData<List<Long>> followingResp = userFeignApi.getFollowingUids(currentUid);
                if (followingResp != null && followingResp.getData() != null) {
                    followingUids = new HashSet<>(followingResp.getData());
                }
            } catch (Exception e) {
                log.warn("获取当前用户关注列表失败, currentUid={}", currentUid, e);
            }
        }

        // 按 uid 分组：统计动态数 + 最近发动态时间，按最近时间倒序
        QueryWrapper<Dynamic> wrapper = new QueryWrapper<>();
        wrapper.select("uid", "COUNT(*) AS dynamicCount", "MAX(create_time) AS latestTime")
                .groupBy("uid")
                .orderByDesc("latestTime");
        List<Map<String, Object>> allRows = this.listMaps(wrapper);

        // 只保留当前用户已关注的 UP 主（动态页 UP 栏只展示关注的）
        List<Map<String, Object>> filtered = new ArrayList<>();
        if (followingUids.isEmpty()) {
            // 未登录或没有关注任何人：不展示任何 UP
            return ResultData.success(Map.of("records", Collections.emptyList(), "total", 0L));
        }
        for (Map<String, Object> row : allRows) {
            Object uidObj = row.get("uid");
            if (uidObj != null && followingUids.contains(Long.valueOf(uidObj.toString()))) {
                filtered.add(row);
            }
        }

        long total = filtered.size();
        int from = (pageNum - 1) * pageSize;
        if (from < 0 || from >= total) {
            return ResultData.success(Map.of("records", Collections.emptyList(), "total", total));
        }
        int to = Math.min(from + pageSize, (int) total);
        List<Map<String, Object>> rows = filtered.subList(from, to);

        List<DynamicUpDTO> list = new ArrayList<>(rows.size());
        if (!CollectionUtils.isEmpty(rows)) {
            List<Long> uids = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Object uidObj = row.get("uid");
                if (uidObj != null) {
                    uids.add(Long.valueOf(uidObj.toString()));
                }
            }

            // 批量获取用户信息
            Map<Long, UserDTO> userMap = new HashMap<>();
            try {
                List<UserDTO> users = userFeignApi.getBatchUserInfo(uids);
                if (!CollectionUtils.isEmpty(users)) {
                    userMap = users.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toMap(UserDTO::getUid, Function.identity(), (a, b) -> a));
                }
            } catch (Exception e) {
                log.warn("批量获取UP主信息失败, uids={}", uids, e);
            }

            for (Map<String, Object> row : rows) {
                Object uidObj = row.get("uid");
                if (uidObj == null) {
                    continue;
                }
                DynamicUpDTO dto = new DynamicUpDTO();
                Long uid = Long.valueOf(uidObj.toString());
                dto.setUid(uid);
                dto.setDynamicCount(row.get("dynamicCount") != null
                        ? Long.valueOf(row.get("dynamicCount").toString()) : 0L);
                if (row.get("latestTime") != null) {
                    try {
                        dto.setLatestTime(LocalDateTime.parse(row.get("latestTime").toString()));
                    } catch (Exception e) {
                        dto.setLatestTime(null);
                    }
                }
                dto.setUser(userMap.get(uid));
                list.add(dto);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        return ResultData.success(result);
    }

    @Override
    public ResultData<String> delete(Long uid, Long id) {
        if (uid == null) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "请先登录");
        }
        if (id == null || id <= 0) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "动态ID不合法");
        }
        Dynamic dynamic = this.getById(id);
        if (dynamic == null) {
            return ResultData.fail(ResultCodeEnum.NOT_FOUND, "动态不存在");
        }
        if (!uid.equals(dynamic.getUid())) {
            return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权限删除该动态");
        }
        boolean removed = this.removeById(id);
        return removed ? ResultData.success("删除成功") : ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "删除失败");
    }

    /**
     * 将 Dynamic 实体转换为 DynamicDTO（已提供 userMap、videoMap、videoStatMap、videoUserMap 提高批量效率）
     */
    private DynamicDTO convertDynamicToDto(Dynamic d,
                                           Map<Long, UserDTO> userMap,
                                           Map<Long, Video> videoMap,
                                           Map<Long, VideoStat> videoStatMap,
                                           Map<Long, UserDTO> videoUserMap) {
        DynamicDTO dto = new DynamicDTO();
        dto.setId(d.getId());
        dto.setUid(d.getUid());
        dto.setTitle(d.getTitle());
        dto.setContent(d.getContent());
        dto.setType(d.getType());
        dto.setVid(d.getVid());
        dto.setParentId(d.getParentId());
        dto.setIsTop(d.getIsTop());
        dto.setCreateTime(d.getCreateTime());
        // 图片列表
        if (StringUtils.hasText(d.getImages())) {
            try {
                dto.setImages(JSON.parseArray(d.getImages(), String.class));
            } catch (Exception e) {
                dto.setImages(Collections.emptyList());
            }
        } else {
            dto.setImages(Collections.emptyList());
        }
        dto.setUser(userMap != null ? userMap.get(d.getUid()) : null);
        // 视频动态附加视频信息
        if (videoMap != null && d.getType() != null && d.getType() >= 1 && d.getVid() != null && videoMap.containsKey(d.getVid())) {
            Video video = videoMap.get(d.getVid());
            Map<String, Object> videoInfo = new HashMap<>();
            videoInfo.put("video", video);
            videoInfo.put("stat", videoStatMap != null ? videoStatMap.getOrDefault(d.getVid(), new VideoStat()) : new VideoStat());
            if (video != null && video.getUid() != null && videoUserMap != null) {
                videoInfo.put("user", videoUserMap.get(video.getUid()));
            }
            dto.setVideo(videoInfo);
        }
        return dto;
    }

    /**
     * 批量填充用户列表的 isFollowing 字段（当前登录用户是否已关注）
     */
    private void fillFollowingStatus(Map<Long, UserDTO> userMap, Set<Long> myFollowingUids) {
        if (CollectionUtils.isEmpty(userMap) || CollectionUtils.isEmpty(myFollowingUids)) {
            return;
        }
        for (UserDTO user : userMap.values()) {
            if (user != null && user.getUid() != null) {
                user.setIsFollowing(myFollowingUids.contains(user.getUid()));
            }
        }
    }

    /**
     * 递归填充转发动态的 parent 链（带环检测，避免循环引用导致栈溢出）
     */
    private void fillParentChain(DynamicDTO dto, Map<Long, DynamicDTO> allDtoMap) {
        if (dto == null || dto.getParentId() == null) {
            return;
        }
        Set<Long> visited = new HashSet<>();
        DynamicDTO current = dto;
        while (current != null && current.getParentId() != null && visited.add(current.getParentId())) {
            DynamicDTO parent = allDtoMap.get(current.getParentId());
            if (parent == null) {
                break;
            }
            current.setParent(parent);
            current = parent;
        }
    }
}
