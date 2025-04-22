package com.hiiro.controller;

import com.hiiro.entity.Danmaku;
import com.hiiro.entity.ResultData;
import com.hiiro.service.DanmakuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 弹幕表 前端控制器
 * </p>
 *
 * @author hiiro
 * @since 2025-03-12
 */
@RestController
@RequestMapping("/api/danmaku")
@Tag(name = "弹幕接口")
public class DanmakuController {

    @Resource
    private DanmakuService danmakuService;

    /**
     * 获取弹幕列表
     *
     * @param vid 视频id
     * @return 弹幕列表
     */
    @GetMapping("/get/{vid}")
    @Operation(summary = "获取弹幕列表")
    public ResultData<List<Danmaku>> getDanmakuList(@PathVariable("vid") Long vid) {
        return danmakuService.getDanmakuList(vid);
    }

    /**
     * 添加弹幕
     *
     * @param danmaku 弹幕
     * @return ResultData对象
     */
    @PostMapping("/send")
    @Operation(summary = "添加弹幕")
    public ResultData<Danmaku> sendDanmaku(@RequestBody Danmaku danmaku) {
        return danmakuService.sendDanmaku(danmaku);
    }
}
