package com.hiiro.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChunkManager {
    @Value("${upload.temp-dir:hirihiri-backend/tmp/uploads}")
    private String tempDir;


    // 在 ChunkManager.java 中添加
    public List<File> getChunks(String uploadId) throws IOException {
        Path chunksDir = Paths.get(tempDir, uploadId, "chunks");
        if (!Files.exists(chunksDir)) {
            return Collections.emptyList();
        }

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
    }


    // 保存分片
    public File saveChunk(String uploadId, int chunkNumber, MultipartFile chunk) throws IOException {
        Path chunkDir = Paths.get(tempDir, uploadId, "chunks");
        // 修正目录创建方式（原错误创建了父目录而非目标目录）
        Files.createDirectories(chunkDir); // 关键修复点
        // 构造分片文件路径（添加文件扩展名）
        Path chunkFile = chunkDir.resolve(chunkNumber + ".part");
        // 在saveChunk方法中添加
        log.info("分片保存路径：{}", chunkFile.toAbsolutePath());
        chunk.transferTo(chunkFile);
        return chunkDir.toFile();
    }

    // 合并分片
    public File mergeChunks(String uploadId) throws IOException {
        Path chunksDir = Paths.get(tempDir, uploadId, "chunks");
        // 在mergeChunks方法中添加
        log.info("开始合并分片，目录：{}", chunksDir.toAbsolutePath());
        // 新增目录存在性检查
        if (!Files.exists(chunksDir) || !Files.isDirectory(chunksDir)) {
            throw new FileNotFoundException("分片目录不存在: " + chunksDir);
        }
        File mergedFile = new File(tempDir, uploadId + "/merged-file.tmp");
        Files.createDirectories(mergedFile.getParentFile().toPath());
        try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
            Files.list(chunksDir)
                    .filter(p -> p.toString().endsWith(".part"))
                    .sorted((a, b) -> {
                        // 更安全的排序方式
                        int numA = Integer.parseInt(a.getFileName().toString().replace(".part", ""));
                        int numB = Integer.parseInt(b.getFileName().toString().replace(".part", ""));
                        return Integer.compare(numA, numB);
                    })
                    .forEach(chunk -> {
                        try {
                            Files.copy(chunk, fos);
                        } catch (IOException e) {
                            throw new UncheckedIOException("合并分片失败: " + chunk, e);
                        }
                    });
        }
        return mergedFile;
    }

    // 清理临时文件
    public void cleanTempFiles(String uploadId) {
        Path uploadDir = Paths.get(tempDir, uploadId);
        try {
            Files.walk(uploadDir)
                    .sorted(Comparator.reverseOrder())
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

