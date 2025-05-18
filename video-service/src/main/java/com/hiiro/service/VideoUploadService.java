package com.hiiro.service;

import com.hiiro.entity.ResultData;
import org.springframework.web.multipart.MultipartFile;

public interface VideoUploadService {

    /**
     * 初始化分片上传
     *
     * @return ResultData对象
     */
    ResultData<String> initUpload();

    /**
     * 上传分片
     *
     * @param chunk       分片文件
     * @param uploadId    上传ID
     * @param chunkNumber 当前分片序号
     * @param totalChunks 总分片数
     * @param fileName    文件名
     * @return ResultData对象
     */
    ResultData<String> uploadChunk(MultipartFile chunk, String uploadId, int chunkNumber, int totalChunks, String fileName);

    /**
     * 完成分片上传
     *
     * @param uploadId      上传ID
     * @param fileName      文件名
     * @param uid           用户ID
     * @param coverFile     封面文件
     * @param videoInfoJson 视频信息JSON
     * @return ResultData对象
     */
    ResultData<String> completeUpload(String uploadId, String fileName, String uid, MultipartFile coverFile, String videoInfoJson);

    /**
     * 取消上传
     *
     * @param uploadId 上传ID
     * @return ResultData对象
     */
    ResultData<String> cancelUpload(String uploadId);
}
