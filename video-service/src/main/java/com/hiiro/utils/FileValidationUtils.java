package com.hiiro.utils;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileValidationUtils {
    // 扩展名白名单
    private static final Map<String, List<String>> EXTENSION_ALLOW_LIST = new HashMap<>();
    // 魔数白名单（前N个字节）
    private static final Map<String, byte[]> MAGIC_NUMBER_ALLOW_LIST = new HashMap<>();

    static {
        // 初始化扩展名白名单
        EXTENSION_ALLOW_LIST.put("image", Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp"));
        EXTENSION_ALLOW_LIST.put("video", Arrays.asList(".mp4", ".avi", ".mov", ".flv", ".mkv"));

        // 初始化魔数白名单（常见文件头）
        MAGIC_NUMBER_ALLOW_LIST.put(".jpg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        MAGIC_NUMBER_ALLOW_LIST.put(".jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8});
        MAGIC_NUMBER_ALLOW_LIST.put(".png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        MAGIC_NUMBER_ALLOW_LIST.put(".gif", new byte[]{0x47, 0x49, 0x46, 0x38});
        MAGIC_NUMBER_ALLOW_LIST.put(".webp", new byte[]{0x57, 0x45, 0x42, 0x50});
        MAGIC_NUMBER_ALLOW_LIST.put(".mp4", new byte[]{0x66, 0x74, 0x79, 0x70});
        MAGIC_NUMBER_ALLOW_LIST.put(".avi", new byte[]{0x52, 0x49, 0x46, 0x46});
    }

    /**
     * 校验文件扩展名
     *
     * @param filename 文件名
     * @param category 文件类型分类（如 image/video）
     * @return 安全扩展名
     */
    public static String validateExtension(String filename, String category) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("文件名无效");
        }

        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex <= 0 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("文件扩展名无效");
        }

        String fileExtension = filename.substring(lastDotIndex).toLowerCase();
        List<String> allowedExtensions = EXTENSION_ALLOW_LIST.getOrDefault(category, List.of());

        if (!allowedExtensions.contains(fileExtension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileExtension);
        }

        return fileExtension;
    }

    /**
     * 校验文件魔数（支持多种输入方式）
     *
     * @param inputStream   输入流（如 MultipartFile.getInputStream()）
     * @param fileExtension 文件扩展名（如 .jpg）
     */
    public static void validateMagicNumber(InputStream inputStream, String fileExtension) throws IOException {
        byte[] expectedMagic = MAGIC_NUMBER_ALLOW_LIST.get(fileExtension);
        if (expectedMagic == null) return;

        // 特殊处理 MP4
        if (".mp4".equals(fileExtension)) {
            byte[] mp4Header = new byte[8];
            int bytesRead = inputStream.read(mp4Header);
            if (bytesRead < 8 || !(mp4Header[4] == 0x66 && mp4Header[5] == 0x74 &&
                    mp4Header[6] == 0x79 && mp4Header[7] == 0x70)) {
                throw new IllegalArgumentException("非法的 MP4 文件格式");
            }
            return;
        }

        // 其他文件类型的通用校验
        if (inputStream.markSupported()) {
            inputStream.mark(expectedMagic.length + 1); // 标记当前位置
        }

        byte[] magicBytes = new byte[expectedMagic.length];
        int bytesRead = inputStream.read(magicBytes);

        if (bytesRead != expectedMagic.length || !Arrays.equals(magicBytes, expectedMagic)) {
            throw new IllegalArgumentException("文件内容不符合预期类型: " + fileExtension);
        }

        if (inputStream.markSupported()) {
            inputStream.reset(); // 重置流指针
        }
    }

    /**
     * 适配 Spring MultipartFile
     */
    public static void validateMagicNumber(MultipartFile file, String fileExtension) throws IOException {
        try (InputStream is = file.getInputStream()) {
            validateMagicNumber(is, fileExtension);
        }
    }

    /**
     * 保留原方法：用于本地文件校验
     */
    public static void validateMagicNumber(File file, String fileExtension) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            validateMagicNumber(is, fileExtension);
        }
    }
}
