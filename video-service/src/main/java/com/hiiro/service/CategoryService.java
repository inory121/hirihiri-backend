package com.hiiro.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hiiro.entity.Category;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CategoryDTO;

import java.util.List;

/**
 * <p>
 * 分区表 服务类
 * </p>
 *
 * @author hiiro
 * @since 2025-01-28
 */
public interface CategoryService extends IService<Category> {

    /**
     * 获取所有分区信息
     * @return ResultData对象
     */
    ResultData<List<CategoryDTO>> getCategory();

    /**
     * 根据主分区id和子分区id获取分区信息
     * @param mcId 主分区id
     * @param scId 子分区id
     * @return Category对象
     */
    Category getCategoryById(String mcId, String scId);

}
