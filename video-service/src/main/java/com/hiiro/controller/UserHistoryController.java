package com.hiiro.controller;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.UserBrowseHistory;
import com.hiiro.entity.dto.HistoryVideoDTO;
import com.hiiro.service.UserBrowseHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户浏览历史控制器
 * </p>
 *
 * @author hiiro
 * @since 2025-06-15
 */
@Slf4j
@RestController
@RequestMapping("/api/history")
@Tag(name = "用户浏览历史接口", description = "用户浏览历史相关接口")
public class UserHistoryController {

    @Resource
    private UserBrowseHistoryService userBrowseHistoryService;

    /**
     * 记录/更新浏览历史
     */
    @PostMapping
    @Operation(summary = "记录浏览历史", description = "记录或更新用户的视频浏览历史")
    public ResultData<String> recordHistory(
            @Parameter(description = "用户ID", required = true) @RequestParam("uid") Integer uid,
            @Parameter(description = "视频ID", required = true) @RequestParam("vid") Integer vid,
            @Parameter(description = "播放进度(秒)", required = false) @RequestParam(value = "progress", defaultValue = "0") Integer progress) {
        
        int result = userBrowseHistoryService.saveOrUpdateHistory(uid, vid, progress);
        if (result > 0) {
            return ResultData.success("记录成功");
        }
        return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR,"记录失败");
    }

    /**
     * 分页获取浏览历史列表
     */
    @GetMapping
    @Operation(summary = "获取浏览历史", description = "分页获取用户的视频浏览历史列表")
    public ResultData<List<HistoryVideoDTO>> getHistoryList(
            @Parameter(description = "用户ID", required = true) @RequestParam("uid") Integer uid,
            @Parameter(description = "页码", required = false) @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小", required = false) @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        
        List<HistoryVideoDTO> historyList = userBrowseHistoryService.getHistoryPageList(uid, pageNum, pageSize);
        return ResultData.success(historyList, "获取成功");
    }

    /**
     * 获取指定视频的浏览进度
     */
    @GetMapping("/progress")
    @Operation(summary = "获取播放进度", description = "获取用户在指定视频上的播放进度")
    public ResultData<Map<String, Object>> getProgress(
            @Parameter(description = "用户ID", required = true) @RequestParam("uid") Integer uid,
            @Parameter(description = "视频ID", required = true) @RequestParam("vid") Integer vid) {
        
        UserBrowseHistory history = userBrowseHistoryService.getHistoryByUidAndVid(uid, vid);
        Map<String, Object> result = new HashMap<>();
        
        if (history != null) {
            result.put("vid", vid);
            result.put("progress", history.getProgress());
            result.put("browseTime", history.getBrowseTime());
            return ResultData.success(result, "获取成功");
        }
        
        result.put("vid", vid);
        result.put("progress", 0);
        result.put("browseTime", null);
        return ResultData.success(result, "暂无浏览记录");
    }
}