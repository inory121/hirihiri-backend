package com.hiiro.utils;

import com.aliyun.oss.model.PartETag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ChunkManager {
    @Value("${video.upload.tmp:tmp/uploads}")
    private String tempDir;

    // 记录文件与上传ID的映射关系
    private final Map<String, String> fileUploadMap = new ConcurrentHashMap<>();

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
                          String fileName,  // 新增参数
                          int totalChunks) {
        try {
            Path chunkDir = Paths.get(tempDir, uploadId, "chunks");
            // 修正目录创建方式（原错误创建了父目录而非目标目录）
            Files.createDirectories(chunkDir); // 关键修复点
            // 构造分片文件路径（添加文件扩展名）
            Path chunkFile = chunkDir.resolve(chunkNumber + ".part");
            // 在saveChunk方法中添加
            log.info("分片保存路径：{}", chunkFile.toAbsolutePath());
            chunk.transferTo(chunkFile);
            // 记录元数据
            if (chunkNumber == 1) {
                Path metaFile = Paths.get(tempDir, uploadId, "metadata.info");
                Files.writeString(metaFile, fileName + ":" + totalChunks);

                // 建立文件映射
                fileUploadMap.put(fileName, uploadId);
                Path recordFile = Paths.get(tempDir, fileName + ".upload");
                Files.writeString(recordFile, uploadId);
            }
        } catch (IOException e) {
            log.error("保存分片失败", e);
        }

    }

    /**
     * 验证分片
     *
     * @param uploadId 上传ID
     * @return 验证结果
     */
    public boolean validateChunks(String uploadId) {
        List<File> chunks = getChunks(uploadId);
        int expectedCount = getTotalChunks(uploadId);

        // 检查分片数量
        if (chunks.size() != expectedCount) return false;

        // 检查分片连续性
        for (int i = 0; i < chunks.size(); i++) {
            int currentNumber = Integer.parseInt(
                    chunks.get(i).getName().replace(".part", "")
            );
            if (currentNumber != (i + 1)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 加载分片状态
     *
     * @param uploadId 上传ID
     * @return 分片状态
     */
    public Map<Integer, PartETag> loadChunkStatus(String uploadId) {
        Path statusFile = Paths.get(tempDir, uploadId, "status.log");
        if (!Files.exists(statusFile)) return Collections.emptyMap();

        try {
            return Files.readAllLines(statusFile).stream()
                    .map(line -> line.split(":"))
                    .collect(Collectors.toMap(
                            arr -> Integer.parseInt(arr[0]),
                            arr -> new PartETag(Integer.parseInt(arr[0]), arr[1])
                    ));
        } catch (IOException e) {
            log.error("加载分片状态失败", e);
        }
        return Map.of();
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
     * 合并分片
     *
     * @param uploadId 上传ID
     * @return 合并后的文件
     */
    public File mergeChunks(String uploadId) {
        Path chunksDir = Paths.get(tempDir, uploadId, "chunks");
        // 在mergeChunks方法中添加
        log.info("开始合并分片，目录：{}", chunksDir.toAbsolutePath());
        // 新增目录存在性检查
        try {
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
        } catch (IOException e) {
            throw new RuntimeException("合并分片失败", e);
        }

    }


//    public String findLatestUploadId(String uploadId) {
//        // 实现1：简单内存缓存（适合开发环境）
////        String uploadId = fileUploadMap.get(fileName);
//
//        // 实现2：持久化存储（生产环境推荐）
//        Path recordFile = Paths.get(tempDir, uploadId + ".upload");
//        if (Files.exists(recordFile)) {
//            try {
//                String latestUploadId = Files.readString(recordFile);
//                if (validateUploadId(latestUploadId)) {
//                    return latestUploadId;
//                }
//            } catch (IOException e) {
//                log.error("读取上传记录失败", e);
//            }
//        }
//        return null;
//    }

//    public double calculateProgress(String uploadId) throws IOException {
//        List<File> chunks = getChunks(uploadId);
//        int total = getTotalChunks(uploadId);
//        return total > 0 ? (chunks.size() * 100.0 / total) : 0;
//    }

//    /**
//     * 验证上传ID
//     *
//     * @param uploadId 上传ID
//     * @return 验证结果
//     */
//    private boolean validateUploadId(String uploadId) {
//        Path statusFile = Paths.get(tempDir, uploadId, "status.log");
//        return Files.exists(statusFile);
//    }

    /**
     * 获取总分片数
     *
     * @param uploadId 上传ID
     * @return 总分片数
     */
    private int getTotalChunks(String uploadId) {
        Path metaFile = Paths.get(tempDir, uploadId, "metadata.info");
        if (Files.exists(metaFile)) {
            String meta;
            try {
                meta = Files.readString(metaFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return Integer.parseInt(meta.split(":")[1]);
        }
        return 0;
    }

//    public void recordChunkStatus(String uploadId, int chunkNumber, PartETag eTag) {
//        Path statusFile = Paths.get(tempDir, uploadId, "status.log");
//        try (BufferedWriter writer = Files.newBufferedWriter(statusFile,
//                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
//            writer.write(String.format("%d:%s\n", chunkNumber, eTag.getETag()));
//        } catch (IOException e) {
//            log.error("分片状态记录失败", e);
//        }
//    }

    /**
     * 清理临时文件
     *
     * @param uploadId 上传ID
     */
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

