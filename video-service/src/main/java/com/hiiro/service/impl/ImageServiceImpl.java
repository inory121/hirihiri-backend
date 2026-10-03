package com.hiiro.service.impl;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.service.ImageService;
import com.hiiro.utils.FileValidationUtils;
import com.hiiro.utils.OSSUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    /**
     * 图片大小上限 5MB（头像/背景均为小图，OSS 层已限制 20MB 请求体）
     */
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024L;

    /**
     * 场景白名单，避免外部传入任意字符串拼进对象 key 造成路径穿越
     */
    private static final Set<String> ALLOWED_SCENES = Set.of("avatar", "background");

    @Resource
    OSSUtil ossUtil;

    /**
     * 上传图片到 OSS，返回可访问 URL
     *
     * @param file  图片文件
     * @param scene 使用场景（avatar / background，非法值回落 avatar）
     * @param uid   当前用户 uid
     * @return ResultData对象，data 为图片访问 URL
     */
    @Override
    public ResultData<String> uploadImage(MultipartFile file, String scene, String uid) {
        if (file == null || file.isEmpty()) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "请选择要上传的图片");
        }
        // uid 由网关注入，仍做纯数字校验，防止异常值污染存储路径
        if (uid == null || !uid.matches("\\d{1,19}")) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "用户标识无效");
        }
        String safeScene = (scene == null || !ALLOWED_SCENES.contains(scene.trim()))
                ? "avatar" : scene.trim();

        try {
            String ext = FileValidationUtils.validateExtension(file.getOriginalFilename(), "image");
            if (file.getSize() > MAX_IMAGE_SIZE) {
                return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "图片大小不能超过5MB");
            }
            FileValidationUtils.validateMagicNumber(file, ext);

            String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String fileName = safeScene + "_" + UUID.randomUUID() + ext;
            String objectKey = String.join("/", date, uid, safeScene, fileName);
            String url = ossUtil.uploadFile(objectKey, file);
            return ResultData.success(url, "上传成功");
        } catch (IOException | IllegalArgumentException e) {
            log.warn("图片校验失败: {}", e.getMessage());
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "图片格式不被支持");
        } catch (Exception e) {
            log.error("图片上传失败, uid={}, scene={}", uid, safeScene, e);
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "图片上传失败");
        }
    }
}
