package com.hiiro.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hiiro.entity.FavoriteFolder;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.service.FavoriteFolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 收藏夹接口
 *
 * @author hiiro
 * @since 2025-06-23
 */
@Tag(name = "收藏夹接口")
@Slf4j
@RestController
@RequestMapping("/api/favorite")
public class FavoriteFolderController {

    @Resource
    private FavoriteFolderService favoriteFolderService;

    /**
     * 获取用户的收藏夹列表
     */
    @Operation(summary = "获取用户收藏夹列表")
    @GetMapping("/folders")
    public ResultData<List<FavoriteFolder>> getUserFolders(
            @RequestParam(value = "vid", required = false) Long vid,
            @RequestParam(value = "uid", required = false) Long targetUid,
            HttpServletRequest request) {
        Long uid;
        boolean isOwner;
        if (targetUid != null) {
            // 查询指定用户的公开收藏夹（不执行初始化逻辑）
            uid = targetUid;
            isOwner = false;
        } else {
            // 查询当前登录用户自己的收藏夹
            String uidStr = request.getHeader("uid");
            if (!StringUtils.hasText(uidStr)) {
                return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
            }
            uid = Long.valueOf(uidStr);
            isOwner = true;
        }
        return favoriteFolderService.getUserFolders(uid, vid, isOwner);
    }

    /**
     * 创建新收藏夹
     */
    @Operation(summary = "创建收藏夹")
    @PostMapping("/folder")
    public ResultData<FavoriteFolder> createFolder(
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "folderName", required = false) String folderName,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {
        
        // 优先从请求体获取参数
        if (body != null && body.containsKey("folderName")) {
            folderName = body.get("folderName");
            if (body.containsKey("description")) {
                description = body.get("description");
            }
        }
        
        if (!StringUtils.hasText(folderName)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "收藏夹名称不能为空");
        }
        
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return favoriteFolderService.createFolder(uid, folderName, description);
    }

    /**
     * 更新收藏夹信息
     */
    @Operation(summary = "更新收藏夹")
    @PutMapping("/folder/{folderId}")
    public ResultData<String> updateFolder(
            @PathVariable("folderId") Long folderId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "folderName", required = false) String folderName,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {
        
        // 优先从请求体获取参数
        if (body != null && body.containsKey("folderName")) {
            folderName = body.get("folderName");
            if (body.containsKey("description")) {
                description = body.get("description");
            }
        }
        
        if (!StringUtils.hasText(folderName)) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "收藏夹名称不能为空");
        }
        
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return favoriteFolderService.updateFolder(uid, folderId, folderName, description);
    }

    /**
     * 删除收藏夹
     */
    @Operation(summary = "删除收藏夹")
    @DeleteMapping("/folder/{folderId}")
    public ResultData<String> deleteFolder(
            @PathVariable("folderId") Long folderId,
            HttpServletRequest request) {
        String uidStr = request.getHeader("uid");
        if (!StringUtils.hasText(uidStr)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
        }
        Long uid = Long.valueOf(uidStr);
        return favoriteFolderService.deleteFolder(uid, folderId);
    }

    /**
     * 获取收藏夹中的视频列表（分页）
     */
    @Operation(summary = "获取收藏夹视频列表")
    @GetMapping("/folder/{folderId}/videos")
    public ResultData<Page<Map<String, Object>>> getFolderVideos(
            @PathVariable("folderId") Long folderId,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        // uid可选，用于区分是否是本人操作（如删除等），查看视频不需要登录
        String uidStr = request.getHeader("uid");
        Long uid = StringUtils.hasText(uidStr) ? Long.valueOf(uidStr) : null;
        return favoriteFolderService.getFolderVideos(uid, folderId, pageNum, pageSize);
    }

    /**
     * 获取用户最近收藏的视频列表
     */
    @Operation(summary = "获取最近收藏的视频")
    @GetMapping("/recent")
    public ResultData<List<Map<String, Object>>> getRecentFavorites(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @RequestParam(value = "uid", required = false) Long targetUid,
            HttpServletRequest request) {
        Long uid;
        if (targetUid != null) {
            // 查询指定用户
            uid = targetUid;
        } else {
            // 查询当前登录用户
            String uidStr = request.getHeader("uid");
            if (!StringUtils.hasText(uidStr)) {
                return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "未登录");
            }
            uid = Long.valueOf(uidStr);
        }
        return favoriteFolderService.getRecentFavorites(uid, limit);
    }
}
