package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.DynamicDTO;
import com.hiiro.entity.dto.DynamicPublishDTO;
import com.hiiro.service.DynamicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * <p>
 * 动态表 前端控制器
 * </p>
 *
 * @author hiiro
 * @since 2026-08-16
 */
@Tag(name = "动态接口")
@Slf4j
@RestController
@RequestMapping("/api/dynamic")
public class DynamicController {

    @Resource
    private DynamicService dynamicService;

    /**
     * 发布动态
     *
     * @param uid 发布者用户ID（网关从 token 中注入）
     * @param dto 动态内容
     * @return ResultData对象
     */
    @Operation(summary = "发布动态")
    @PostMapping("/publish")
    public ResultData<String> publish(@RequestHeader("uid") String uid,
                                      @RequestBody DynamicPublishDTO dto) {
        return dynamicService.publish(Long.parseLong(uid), dto);
    }

    /**
     * 分页获取动态列表
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @param type     类型 0全部 1视频投稿
     * @return ResultData对象
     */
    @Operation(summary = "分页获取动态列表")
    @GetMapping("/list")
    public ResultData<Map<String, Object>> getDynamicList(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                          @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                          @RequestParam(name = "type", required = false) Integer type,
                                                          @RequestParam(name = "uid", required = false) Long uid,
                                                          @RequestParam(name = "keyword", required = false) String keyword,
                                                          @RequestHeader(value = "uid", required = false) String myUid) {
        Long currentUid = (myUid != null && !myUid.isEmpty()) ? Long.parseLong(myUid) : null;
        return dynamicService.getDynamicList(pageNum, pageSize, type, uid, currentUid, keyword);
    }

    /**
     * 分页获取发过动态的UP主列表
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "分页获取发过动态的UP主列表（仅当前用户已关注的）")
    @GetMapping("/up-list")
    public ResultData<Map<String, Object>> getUpList(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                     @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                     @RequestHeader(value = "uid", required = false) String myUid) {
        Long currentUid = null;
        if (myUid != null && !myUid.isEmpty()) {
            try {
                currentUid = Long.parseLong(myUid);
            } catch (NumberFormatException ignored) {
            }
        }
        return dynamicService.getUpList(pageNum, pageSize, currentUid);
    }

    /**
     * 删除动态（仅发布者本人可删除）
     *
     * @param uid 当前登录用户ID（网关从 token 中注入）
     * @param id  动态ID
     * @return ResultData对象
     */
    @Operation(summary = "删除动态")
    @GetMapping("/delete")
    public ResultData<String> deleteDynamic(@RequestHeader("uid") String uid,
                                            @RequestParam("id") Long id) {
        return dynamicService.delete(Long.parseLong(uid), id);
    }

    /**
     * 点赞/取消点赞动态（幂等切换）
     *
     * @param dynamicId 动态ID
     * @param uid       当前登录用户ID（网关从 token 中注入）
     * @return {liked, likeCount}
     */
    @Operation(summary = "点赞/取消点赞动态")
    @PostMapping("/like/{dynamicId}")
    public ResultData<Map<String, Object>> toggleLike(@PathVariable("dynamicId") Long dynamicId,
                                                      @RequestHeader("uid") String uid) {
        return dynamicService.toggleLike(dynamicId, Long.parseLong(uid));
    }

    /**
     * 未读动态列表：当前用户关注的UP主新发布的视频投稿（带 unread 标记）
     *
     * @param myUid 当前登录用户ID（网关从 token 中注入）
     * @return {records, total}
     */
    @Operation(summary = "未读动态列表（关注的UP主新投稿）")
    @GetMapping("/unread-list")
    public ResultData<Map<String, Object>> getUnreadList(@RequestHeader(value = "uid", required = false) String myUid) {
        Long currentUid = (myUid != null && !myUid.isEmpty()) ? Long.parseLong(myUid) : null;
        return dynamicService.getUnreadList(currentUid);
    }

    /**
     * 动态详情（单条）：支持未登录访问，未登录时 liked/isFollowing 为未登录态
     *
     * @param dynamicId 动态ID
     * @param myUid     当前登录用户ID（网关从 token 中注入，可为空）
     * @return 动态详情DTO（含发布者/视频/点赞/评论/转发数，转发动态含 parent 链）
     */
    @Operation(summary = "动态详情")
    @GetMapping("/{dynamicId}")
    public ResultData<DynamicDTO> getDetail(@PathVariable("dynamicId") Long dynamicId,
                                            @RequestHeader(value = "uid", required = false) String myUid) {
        Long currentUid = (myUid != null && !myUid.isEmpty()) ? Long.parseLong(myUid) : null;
        return dynamicService.getDynamicDetail(dynamicId, currentUid);
    }

    /**
     * 动态「赞与转发」用户列表：合并点赞与转发记录按时间倒序分页（赞/转发分开计数、不去重）
     *
     * @param dynamicId 动态ID
     * @param pageNum   页码（可选，默认 1）
     * @param pageSize  每页条数（可选，默认 20）
     * @param myUid     当前登录用户ID（网关从 token 注入，可为空）
     * @return {records, total, likeCount, repostCount}
     */
    @Operation(summary = "动态赞与转发用户列表")
    @GetMapping("/{dynamicId}/interactions")
    public ResultData<Map<String, Object>> getInteractions(@PathVariable("dynamicId") Long dynamicId,
                                                           @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                           @RequestParam(name = "pageSize", required = false) Integer pageSize,
                                                           @RequestHeader(value = "uid", required = false) String myUid) {
        Long currentUid = (myUid != null && !myUid.isEmpty()) ? Long.parseLong(myUid) : null;
        return dynamicService.getInteractions(dynamicId, currentUid, pageNum, pageSize);
    }
}
