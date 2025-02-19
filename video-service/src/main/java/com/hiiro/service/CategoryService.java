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

}
