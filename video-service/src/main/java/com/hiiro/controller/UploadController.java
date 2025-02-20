package com.hiiro.controller;

import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.utils.OSSUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class UploadController {

    @Resource
    private OSSUtil ossUtil;

    @PostMapping("/upload")
    public ResultData<String> uploadFile(@RequestParam("file") MultipartFile multipartFile) {
        try {
            // 获取项目的根目录
            Path projectDir = Paths.get(System.getProperty("user.dir")); // 使用系统属性获取项目根目录
            Path tempDir = projectDir.resolve("hirihiri-backend/temp"); // 创建一个名为 "temp" 的子目录

            // 如果目录不存在，则创建它
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir); // 创建目录（包括父目录）
                System.out.println("临时目录创建成功: " + tempDir.toAbsolutePath());
            } else {
                System.out.println("临时目录已存在: " + tempDir.toAbsolutePath());
            }

            // 使用 java.nio.file.Files 创建临时文件
            Path tempFile = Files.createTempFile(tempDir, "upload-", ".tmp");
            System.out.println("临时文件路径: " + tempFile.toAbsolutePath());

            // 将上传的文件保存到临时文件
            multipartFile.transferTo(tempFile.toFile());

            // 调用上传服务
            ossUtil.uploadFileMultipart(multipartFile.getOriginalFilename(), tempFile.toFile());

            return ResultData.success("上传文件成功");
        } catch (IOException e) {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
