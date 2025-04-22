package com.hiiro.service;

import com.hiiro.entity.Danmaku;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.ResultData;

import java.util.List;

/**
 * <p>
 * 弹幕表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2025-03-12
 */
public interface DanmakuService extends IService<Danmaku> {


    /**
     * 获取弹幕列表
     *
     * @param vid 视频id
     * @return 弹幕列表
     */
    ResultData<List<Danmaku>> getDanmakuList(Long vid);

    /**
     * 添加弹幕
     *
     * @param danmaku 弹幕
     * @return ResultData对象
     */
    ResultData<Danmaku> sendDanmaku(Danmaku danmaku);
}
