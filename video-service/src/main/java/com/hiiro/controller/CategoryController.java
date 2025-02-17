package com.hiiro.controller;

import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CategoryDTO;
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
@RequestMapping("/api/category")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    /**
     *
     * @return ResultData对象
     */
    @Operation(summary = "获取所有分区")
    @GetMapping("/get/all")
    public ResultData<List<CategoryDTO>> getCategory() {
        return categoryService.getCategory();
    }

}
