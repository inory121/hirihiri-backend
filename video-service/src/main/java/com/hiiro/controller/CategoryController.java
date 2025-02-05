package com.hiiro.controller;

import com.hiiro.entity.Category;
import com.hiiro.entity.ResultData;
import com.hiiro.exp.VideoNotFoundException;
import com.hiiro.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 分区表 前端控制器
 * </p>
 *
 * @author hiiro
 * @since 2025-01-28
 */
@Tag(name = "分区信息管理")
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    @Operation(summary = "获取所有分区")
    @GetMapping("/get/all")
    public ResultData<List<Category>> getCategory() {
        List<Category> categoryList = categoryService.findAll();
        return ResultData.success(categoryList);
    }

    @GetMapping("/error")
    public void error() throws VideoNotFoundException {
        throw new VideoNotFoundException("视频未找到");
    }
}
