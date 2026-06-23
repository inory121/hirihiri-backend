package com.hiiro.controller;

import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.Video;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.service.VideoService;
import com.hiiro.service.VideoStatService;
import com.hiiro.service.impl.OnlineViewerService;
import com.hiiro.utils.RedisUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 视频表
 * </p>
 *
 * @author hiiro
 * @since 2025-02-11
 */
@Tag(name = "视频接口")
@Slf4j
@RestController
@RequestMapping("/api/video")
public class VideoController {

    @Resource
    private VideoService videoService;

    @Resource
    private VideoStatService videoStatService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private OnlineViewerService onlineViewerService;

    @Resource
    private UserFeignApi userFeignApi;

    /**
     * 获取推荐视频
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "分页获取推荐视频")
    @GetMapping("/recommend")
    public ResultData<List<Map<String, Object>>> getRecommendVideos(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                                    @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return videoService.getRecommendVideos(pageNum, pageSize);
    }

    /**
     * 获取全部视频
     *
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "分页获取全部视频")
    @GetMapping("/all")
    public ResultData<List<Map<String, Object>>> getAllVideos(@RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                              @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return videoService.getAllVideos(pageNum, pageSize);
    }

    /**
     * 获取视频详情
     *
     * @param vid 视频id
     * @return ResultData对象
     */
    @Operation(summary = "获取视频详情")
    @GetMapping("/{vid}")
    public ResultData<HashMap<String, Object>> getVideoById(@PathVariable("vid") Long vid) {
        return videoService.getVideoById(vid);
    }

    /**
     * 更新视频状态（删除/审核等）
     * 仅视频作者或管理员可操作
     *
     * @param video 视频对象
     * @param request HTTP请求对象
     * @return ResultData对象
     */
    @Operation(summary = "更新视频状态")
    @PostMapping("/update/status")
    public ResultData<Video> updateVideoStatus(@RequestBody Video video, HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "请先登录");
        }
        Long currentUid = Long.valueOf(uidStr);

        Video existingVideo = videoService.getById(video.getVid());
        if (existingVideo == null) {
            return ResultData.fail(ResultCodeEnum.VIDEO_NOT_EXIST, "视频不存在");
        }

        if (!existingVideo.getUid().equals(currentUid)) {
            ResultData<UserDTO> userResult = userFeignApi.getUserByUid(currentUid);
            if (userResult == null || userResult.getData() == null) {
                return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权限操作此视频");
            }
            Byte role = userResult.getData().getRole();
            if (role == null || (role != 1 && role != 2)) {
                return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权限操作此视频");
            }
        }

        return videoService.updateVideoStatus(video.getVid(), video.getStatus());
    }

    /**
     * 搜索视频
     *
     * @param keyword  关键词
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "搜索视频")
    @GetMapping("/search")
    public ResultData<List<Map<String, Object>>> searchVideos(@RequestParam("keyword") String keyword,
                                                              @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                              @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        if (keyword == null || keyword.trim().isEmpty() || keyword.length() > 100) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "搜索词长度必须在1-100字符之间");
        }
        return videoService.searchVideos(keyword.trim(), pageNum, pageSize);
    }

    /**
     * 上报视频播放量
     *
     * @param vid     视频ID
     * @param request HTTP请求对象
     * @return ResultData对象
     */
    @Operation(summary = "上报视频播放量")
    @PostMapping("/play")
    public ResultData<Boolean> reportPlay(@RequestParam("vid") Long vid,
                                          HttpServletRequest request) {
        // 获取用户标识: 优先使用uid(已登录),否则使用IP地址
        String uid = request.getHeader("uid");
        String identifier;
        if (uid != null && !uid.isEmpty()) {
            identifier = "user:" + uid;
        } else {
            identifier = "ip:" + getClientIp(request);
        }

        // Redis去重: 同一用户/IP 24小时内只计一次播放
        String key = "play:" + vid + ":" + identifier;
        boolean isNew = redisUtil.setIfAbsent(key, "1", 24, TimeUnit.HOURS);

        if (isNew) {
            videoStatService.incrementPlay(vid);
        }

        return ResultData.success(isNew, "播放量上报成功");
    }

    /**
     * 在线观众心跳
     *
     * @param vid      视频ID
     * @param viewerId 观众标识 (前端传入: uid 或 cookie UUID)
     * @return 当前在线人数
     */
    @Operation(summary = "在线观众心跳")
    @PostMapping("/heartbeat")
    public ResultData<Long> heartbeat(@RequestParam("vid") Long vid, @RequestParam("viewerId") String viewerId) {
        long onlineCount = onlineViewerService.heartbeat(vid, viewerId);
        return ResultData.success(onlineCount, "心跳成功");
    }

    /**
     * 按用户ID获取投稿视频
     *
     * @param uid      用户ID
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Operation(summary = "按用户ID获取投稿视频")
    @GetMapping("/user/{uid}")
    public ResultData<List<Map<String, Object>>> getVideosByUid(@PathVariable("uid") Long uid,
                                                            @RequestParam(name = "pageNum", required = false) Integer pageNum,
                                                            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return videoService.getVideosByUid(uid, pageNum, pageSize);
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        // IPv6环回地址统一转为127.0.0.1
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }
        // IPv4映射的IPv6地址(如 ::ffff:192.168.1.1)转为IPv4
        if (ip != null && ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }
        return ip;
    }
}
