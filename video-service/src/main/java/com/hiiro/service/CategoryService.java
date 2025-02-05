package com.hiiro.service;

import com.hiiro.entity.Category;
import com.baomidou.mybatisplus.extension.service.IService;

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

    List<Category> findAll();

}
