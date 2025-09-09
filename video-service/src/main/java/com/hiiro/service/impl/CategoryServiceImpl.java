package com.hiiro.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Category;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CategoryDTO;
import com.hiiro.mapper.CategoryMapper;
import com.hiiro.service.CategoryService;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * <p>
 * 分区表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-01-28
 */
@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Resource
    private RedisUtil redisUtil;

    private static final String CACHE_KEY = "categoryList";

    private static final List<String> MAIN_CATEGORY_ORDER = Arrays.asList(
            "anime", "movie", "guochuang", "tv", "variety", "documentary",
            "douga", "game", "kichiku", "music", "dance", "cinephile",
            "ent", "knowledge", "tech", "information", "food", "life",
            "car", "fashion", "sports", "animal"
    );

    /**
     * 获取所有分类信息
     *
     * @return 分类信息
     */
    @Override
    public ResultData<List<CategoryDTO>> getCategory() {
        long startTime = System.currentTimeMillis();
        // 1.优先从缓存获取
        List<CategoryDTO> cachedList = redisUtil.getList(CACHE_KEY, -1, CategoryDTO.class);
        if (!cachedList.isEmpty()) {
            log.info("从缓存获取分类信息成功，耗时：{}ms", System.currentTimeMillis() - startTime);
            return ResultData.success(cachedList, "获取分类信息成功");
        }
        
        // 2.缓存未命中，查询数据库
        List<Category> dbList = this.list();
        if (CollectionUtils.isEmpty(dbList)) {
            log.warn("数据库分类信息为空");
            return ResultData.fail(ResultCodeEnum.CATEGORY_NOT_EXIST, "视频分类信息不存在");
        }
        
        // 3. 转换数据结构
        Map<String, CategoryDTO> categoryMap = convertToCategoryMap(dbList);
        // 4. 按预定顺序排序
        List<CategoryDTO> sortedCategories = sortCategories(categoryMap);
        // 5. 更新缓存
        if (!redisUtil.setList(CACHE_KEY, sortedCategories)){
            log.info("更新缓存失败");
        }
        log.info("分类信息查询+处理总耗时：{}ms", System.currentTimeMillis() - startTime);
        return ResultData.success(sortedCategories, "获取视频分区成功");
    }

    /**
     * 将数据库实体列表转换为按主分类分组的Map结构
     */
    private Map<String, CategoryDTO> convertToCategoryMap(List<Category> categories) {
        Map<String, CategoryDTO> categoryMap = new HashMap<>();

        for (Category category : categories) {
            String mcId = category.getMcId();

            // 初始化主分类DTO（如果不存在）
            categoryMap.computeIfAbsent(mcId, k -> {
                CategoryDTO dto = new CategoryDTO();
                dto.setMcId(mcId);
                dto.setMcName(category.getMcName());
                dto.setScList(new ArrayList<>());
                return dto;
            });

            // 添加子分类信息
            categoryMap.get(mcId).getScList().add(createSubCategoryMap(category));
        }

        return categoryMap;
    }

    /**
     * 创建子分类的Map结构
     */
    private Map<String, Object> createSubCategoryMap(Category category) {
        return Map.of(
                "cid", category.getCId(),
                "mcId", category.getMcId(),
                "mcName", category.getMcName(),
                "scId", category.getScId(),
                "scName", category.getScName(),
                "descr", category.getDescr(),
                "rcmTag", Arrays.asList(category.getRcmTag().split("\n"))
        );
    }

    /**
     * 按预定顺序对分类进行排序
     */
    private List<CategoryDTO> sortCategories(Map<String, CategoryDTO> categoryMap) {
        List<CategoryDTO> sortedList = new ArrayList<>();

        // 按预定义顺序添加主分类
        for (String mcId : MAIN_CATEGORY_ORDER) {
            if (categoryMap.containsKey(mcId)) {
                sortedList.add(categoryMap.get(mcId));
            }
        }

        return sortedList;
    }

    /**
     * 根据主分区id和子分区id获取分类信息
     *
     * @param mcId 主分区id
     * @param scId 子分区id
     * @return Category对象
     */
    @Override
    public Category getCategoryById(String mcId, String scId) {
        return this.lambdaQuery()
                .eq(Category::getMcId, mcId)
                .eq(Category::getScId, scId)
                .oneOpt()
                .orElse(new Category());
    }
}