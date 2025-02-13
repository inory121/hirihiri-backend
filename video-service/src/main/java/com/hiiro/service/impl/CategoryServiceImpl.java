package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Category;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.dto.CategoryDTO;
import com.hiiro.mapper.CategoryMapper;
import com.hiiro.service.CategoryService;
import com.hiiro.utils.RedisUtil;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    @Resource
    private RedisUtil redisUtil;

    @Override
    public ResultData<List<CategoryDTO>> getCategory() {
        List<CategoryDTO> categorys = redisUtil.getList("categoryList",0,CategoryDTO.class);
        if (Objects.nonNull(categorys)) {
            return ResultData.success(categorys);
        }
        QueryWrapper<Category> wrapper = new QueryWrapper<>();
        List<Category> list = categoryMapper.selectList(wrapper);
        Map<String, CategoryDTO> categoryDTOMap = new HashMap<>();
        List<CategoryDTO> sortedCategories = new ArrayList<>();
        if (Objects.nonNull(list)) {
            for (Category category : list) {
                Integer cId = category.getCId();
                String mcId = category.getMcId();
                String scId = category.getScId();
                String mcName = category.getMcName();
                String scName = category.getScName();
                String descr = category.getDescr();
                String[] rcmTag = category.getRcmTag().split("\n");
                List<String> rcmTags = Arrays.asList(rcmTag);

                if (!categoryDTOMap.containsKey(mcId)) {
                    CategoryDTO categoryDTO = new CategoryDTO();
                    // categoryDTO.setCId(cId);
                    categoryDTO.setMcId(mcId);
                    categoryDTO.setMcName(mcName);
                    categoryDTO.setScList(new ArrayList<>());
                    categoryDTOMap.put(mcId, categoryDTO);
                }

                HashMap<String, Object> scMap = new HashMap<>(Map.of(
                        "cid", cId,
                        "mcId", mcId,
                        "scId", scId,
                        "scName", scName,
                        "descr", descr,
                        "rcmTag", rcmTags));
                categoryDTOMap.get(mcId).getScList().add(scMap);
            }
            List<String> sortOrder = Arrays.asList("anime", "movie", "guochuang", "tv", "variety", "documentary",
                    "douga", "game", "kichiku", "music", "dance", "cinephile", "ent", "knowledge", "tech",
                    "information", "food", "life", "car", "fashion", "sports", "animal");

            for (String mcId : sortOrder) {
                if (categoryDTOMap.containsKey(mcId)) {
                    sortedCategories.add(categoryDTOMap.get(mcId));
                }
            }
            redisUtil.delete("categoryList");
            redisUtil.setAllList("categoryList", sortedCategories);
        } else {
            return ResultData.fail(ResultCodeEnum.DATABASE_SELECT_ERROR);
        }

        return ResultData.success(sortedCategories);
    }
}
