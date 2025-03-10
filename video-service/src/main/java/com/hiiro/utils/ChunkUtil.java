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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class ChunkUtil {
    @Value("${video.upload.tmp:tmp/uploads}")
    private String tempDir;

    // 记录文件与上传ID的映射关系
//    private final Map<String, String> fileUploadMap = new ConcurrentHashMap<>();

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

        try {
            return Files.list(chunksDir)
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
     * 清理临时文件
     *
     * @param uploadId 上传ID
     */
    public void cleanTempFiles(String uploadId) {
        Path uploadDir = Paths.get(tempDir, uploadId);
//        Path uploadDir = Paths.get(tempDir, uploadId, "chunks");
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

