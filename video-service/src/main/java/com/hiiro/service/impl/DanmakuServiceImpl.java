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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private VideoStatService videoStatService;

    @Override
    public ResultData<List<Danmaku>> getDanmakuList(Long vid) {
        // 直接使用父类的 baseMapper
        List<Danmaku> danmakuList = this.baseMapper.selectList(
                new LambdaQueryWrapper<Danmaku>().eq(Danmaku::getVid, vid)
        );
        if (danmakuList == null || danmakuList.isEmpty()) {
            return ResultData.fail(ResultCodeEnum.DANMAKU_NOT_EXIST, "弹幕不存在");
        }
        return ResultData.success(danmakuList);
    }

    @Override
    @Transactional
    public ResultData<Danmaku> sendDanmaku(Danmaku danmaku) {
        if (this.save(danmaku) && videoStatService.incrementDanmaku(danmaku.getVid()) == 1) {
            return ResultData.success(danmaku, "发送成功");
        }
        throw new RuntimeException("发送失败");  // 触发事务回滚
    }
}
