package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Dynamic;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.DynamicDTO;
import com.hiiro.entity.dto.DynamicPublishDTO;

import java.util.Map;

/**
 * <p>
 * 动态表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
public interface DynamicService extends IService<Dynamic> {

    /**
     * 发布动态
     *
     * @param uid 发布者用户ID
     * @param dto 动态内容
     * @return 发布结果
     */
    ResultData<String> publish(Long uid, DynamicPublishDTO dto);

    /**
     * 分页获取动态列表
     *
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @param type       类型 0全部 1视频投稿
     * @param uid        发布者UID过滤（null表示全部）
     * @param currentUid 当前登录用户UID（用于填充isFollowing，null表示未登录）
     * @param keyword    关键字（为空则不过滤，匹配标题/正文）
     * @return 动态列表 {records, total}
     */
    ResultData<Map<String, Object>> getDynamicList(Integer pageNum, Integer pageSize, Integer type, Long uid, Long currentUid, String keyword);

    /**
     * 分页获取发过动态的UP主列表（按最近发动态时间倒序）
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return {records: [DynamicUpDTO], total}
     */
    ResultData<Map<String, Object>> getUpList(Integer pageNum, Integer pageSize, Long currentUid);

    /**
     * 删除动态（仅动态发布者本人可删除）
     *
     * @param uid 当前登录用户ID
     * @param id  动态ID
     * @return 删除结果
     */
    ResultData<String> delete(Long uid, Long id);

    /**
     * 点赞/取消点赞动态（幂等切换）
     *
     * @param dynamicId 动态ID
     * @param uid       当前登录用户ID
     * @return {liked: boolean, likeCount: int}
     */
    ResultData<Map<String, Object>> toggleLike(Long dynamicId, Long uid);

    /**
     * 投稿视频动态生成后，给所有粉丝发送 dynamic 类型通知
     * （配合 WebSocket UNREAD_UPDATED 推送，实时驱动头部动态红点）
     *
     * @param uid       投稿者 uid
     * @param dynamicId 动态ID
     * @param vid       视频ID
     * @param title     视频标题（通知摘要）
     * @param coverUrl  视频封面（extJson，供弹窗缩略图用）
     */
    void notifyVideoDynamicToFollowers(Long uid, Long dynamicId, Long vid, String title, String coverUrl);

    /**
     * 未读动态列表：当前用户关注的UP主新发布的视频投稿（根据未读通知反查），带 unread 标记
     *
     * @param currentUid 当前登录用户UID
     * @return {records: [DynamicDTO], total}
     */
    ResultData<Map<String, Object>> getUnreadList(Long currentUid);

    /**
     * 动态详情（单条）：复用列表的批量组装逻辑（发布者/视频/点赞/评论/转发数），
     * 转发动态（type=3）额外填充被转发原动态链
     *
     * @param dynamicId  动态ID
     * @param currentUid 当前登录用户UID（用于填充liked/isFollowing，null表示未登录）
     * @return 动态详情DTO
     */
    ResultData<DynamicDTO> getDynamicDetail(Long dynamicId, Long currentUid);

    /**
     * 动态「赞与转发」用户列表：合并点赞与转发记录，按操作时间倒序分页。
     * 赞与转发分开计数、不去重（同一用户既赞又转发会出现两条）。
     *
     * @param dynamicId  动态ID
     * @param currentUid 当前登录用户UID（用于填充 isFollowing，null 表示未登录）
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return {records: [{user, action(like|repost), time}], total, likeCount, repostCount}
     */
    ResultData<Map<String, Object>> getInteractions(Long dynamicId, Long currentUid, Integer pageNum, Integer pageSize);
}
