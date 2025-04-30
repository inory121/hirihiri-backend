package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Danmaku;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.mapper.DanmakuMapper;
import com.hiiro.service.DanmakuService;
import com.hiiro.service.VideoStatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 弹幕表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-03-12
 */
@Service
public class DanmakuServiceImpl extends ServiceImpl<DanmakuMapper, Danmaku> implements DanmakuService {

    @Resource
    DanmakuMapper danmakuMapper;
    @Resource
    VideoStatService videoStatService;

    /**
     * 获取弹幕列表
     *
     * @param vid 视频id
     * @return 弹幕列表
     */
    @Override
    public ResultData<List<Danmaku>> getDanmakuList(Long vid) {
        List<Danmaku> danmakuList = danmakuMapper.selectList(new LambdaQueryWrapper<Danmaku>().eq(Danmaku::getVid, vid));
        if (Objects.isNull(danmakuList)) {
            return ResultData.fail(ResultCodeEnum.DANMAKU_NOT_EXIST, "获取弹幕信息失败");
        }
        return ResultData.success(danmakuList, "获取弹幕信息成功");
    }

    /**
     * 添加弹幕
     *
     * @param danmaku 弹幕
     * @return ResultData对象
     */
    @Override
    public ResultData<Danmaku> sendDanmaku(Danmaku danmaku) {
        if (danmakuMapper.insert(danmaku) == 1 && videoStatService.incrementDanmaku(danmaku.getVid()) == 1) {
            return ResultData.success(danmaku,"发送弹幕成功");
        } else {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "发送弹幕失败");
        }
    }
}
