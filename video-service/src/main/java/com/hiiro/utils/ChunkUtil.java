package com.hiiro.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class ChunkUtil {
    @Value("${video.upload.tmp:tmp/uploads}")
    private String tempDir;

    /**
     * 保存分片
     *
     * @param uploadId    上传ID
     * @param chunkNumber 分片序号
     * @param chunk       文件分片
     * @param fileName    文件名
     * @param totalChunks 总分片数
     */
    public void saveChunk(String uploadId,
                          int chunkNumber,
                          MultipartFile chunk,
                          String fileName,
                          int totalChunks) {
        try {
            Path chunkDir = Paths.get(tempDir, uploadId, "chunks");
            // 目录创建
            Files.createDirectories(chunkDir);
            // 构造分片文件路径（添加文件扩展名）
            Path chunkFile = chunkDir.resolve(chunkNumber + ".part");

            log.info("分片{}.part 保存成功，保存路径：{}", chunkNumber, chunkFile.toAbsolutePath());
            chunk.transferTo(chunkFile);
            // 记录元数据
            if (chunkNumber == 1) {
                Path metaFile = Paths.get(tempDir, uploadId, "metadata.info");
                Files.writeString(metaFile, fileName + ":" + totalChunks);
            }
        } catch (IOException e) {
            log.error("保存分片失败", e);
        }

    }

    /**
     * 获取分片文件列表
     *
     * @param uploadId 上传ID
     * @return 分片文件列表
     */
    public List<File> getChunks(String uploadId) {
        Path chunksDir = Paths.get(tempDir, uploadId, "chunks");
        if (!Files.exists(chunksDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(chunksDir)) {
            return paths
                    .filter(p -> p.getFileName().toString().endsWith(".part"))
                    .sorted((a, b) -> {
                        // 按数字顺序排序（与合并逻辑一致）
                        int numA = Integer.parseInt(a.getFileName().toString().replace(".part", ""));
                        int numB = Integer.parseInt(b.getFileName().toString().replace(".part", ""));
                        return Integer.compare(numA, numB);
                    })
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("获取分片文件列表失败", e);
        }
        return List.of();
    }

    /**
     * 获取上传状态（用于断点续传）
     * 返回 Map: uploadedChunks(已上传分片序号列表), fileName(文件名), totalChunks(总分片数), exists(uploadId是否有效)
     *
     * @param uploadId 上传ID
     * @return 上传状态信息
     */
    public Map<String, Object> getUploadStatus(String uploadId) {
        Map<String, Object> status = new HashMap<>();
        Path uploadDir = Paths.get(tempDir, uploadId);

        if (!Files.exists(uploadDir)) {
            status.put("exists", false);
            status.put("uploadedChunks", List.of());
            status.put("fileName", "");
            status.put("totalChunks", 0);
            return status;
        }

        status.put("exists", true);

        // 读取 metadata.info
        Path metaFile = uploadDir.resolve("metadata.info");
        if (Files.exists(metaFile)) {
            try {
                String meta = Files.readString(metaFile);
                int colonIdx = meta.lastIndexOf(':');
                if (colonIdx > 0) {
                    status.put("fileName", meta.substring(0, colonIdx));
                    status.put("totalChunks", Integer.parseInt(meta.substring(colonIdx + 1)));
                }
            } catch (IOException e) {
                log.warn("读取 metadata 失败: {}", uploadId, e);
                status.put("fileName", "");
                status.put("totalChunks", 0);
            }
        } else {
            status.put("fileName", "");
            status.put("totalChunks", 0);
        }

        // 读取已上传分片
        Path chunksDir = uploadDir.resolve("chunks");
        if (!Files.exists(chunksDir)) {
            status.put("uploadedChunks", java.util.List.of());
            return status;
        }
        try (Stream<Path> paths = Files.list(chunksDir)) {
            java.util.List<Integer> uploaded = paths
                    .filter(p -> p.getFileName().toString().endsWith(".part"))
                    .map(p -> Integer.parseInt(p.getFileName().toString().replace(".part", "")))
                    .sorted()
                    .collect(Collectors.toList());
            status.put("uploadedChunks", uploaded);
        } catch (IOException e) {
            log.error("读取分片列表失败", e);
            status.put("uploadedChunks", java.util.List.of());
        }
        return status;
    }

    /**
     * 清理临时文件
     *
     * @param uploadId 上传ID
     */
    public void cleanTempFiles(String uploadId) {
        Path uploadDir = Paths.get(tempDir, uploadId);
        if (!Files.exists(uploadDir)) {
            log.info("临时目录不存在，无需清理: {}", uploadDir);
            return;
        }
        try (Stream<Path> stream = Files.walk(uploadDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            System.err.println("文件删除失败: " + path);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("清理临时文件失败", e);
        }
    }

}