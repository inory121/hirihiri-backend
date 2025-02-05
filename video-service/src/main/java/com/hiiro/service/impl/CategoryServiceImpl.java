package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Category;
import com.hiiro.mapper.CategoryMapper;
import com.hiiro.service.CategoryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 分区表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-01-28
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Resource
    private CategoryMapper categoryMapper;

    @Override
    public List<Category> findAll() {
        QueryWrapper<Category> wrapper = new QueryWrapper<>();
        return categoryMapper.selectList(wrapper);
    }
}
