package com.hiiro.service;

import com.hiiro.entity.ResultData;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用图片上传服务（头像、主页背景等小图，走 OSS 直传）
 */
public interface ImageService {

    /**
     * 上传图片
     *
     * @param file  图片文件（jpg/jpeg/png/gif/webp，大小上限 5MB）
     * @param scene 使用场景，白名单：avatar / background
     * @param uid   当前用户 uid（用于存储路径隔离）
     * @return ResultData对象，data 为图片访问 URL
     */
    ResultData<String> uploadImage(MultipartFile file, String scene, String uid);
}
