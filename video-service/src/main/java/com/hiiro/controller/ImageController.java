package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用图片上传（头像、主页背景）—— 需登录，网关校验 token 后注入 uid
 */
@Tag(name = "图片上传")
@RestController
@RequestMapping("/api/image")
public class ImageController {

    @Resource
    private ImageService imageService;

    /**
     * 上传图片
     *
     * @param file  图片文件
     * @param scene 使用场景：avatar（默认）/ background
     * @param uid   当前登录用户 uid（网关注入）
     * @return ResultData对象，data 为图片 URL
     */
    @Operation(summary = "上传图片")
    @PostMapping("/upload")
    public ResultData<String> uploadImage(@RequestPart("file") MultipartFile file,
                                         @RequestParam(name = "scene", required = false) String scene,
                                         @RequestHeader("uid") String uid) {
        return imageService.uploadImage(file, scene, uid);
    }
}
