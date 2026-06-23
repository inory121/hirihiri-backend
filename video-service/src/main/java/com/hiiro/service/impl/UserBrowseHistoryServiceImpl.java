package com.hiiro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.UserBrowseHistory;
import com.hiiro.entity.dto.HistoryVideoDTO;
import com.hiiro.mapper.UserBrowseHistoryMapper;
import com.hiiro.service.UserBrowseHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户浏览历史服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-06-15
 */
@Slf4j
@Service
public class UserBrowseHistoryServiceImpl extends ServiceImpl<UserBrowseHistoryMapper, UserBrowseHistory> implements UserBrowseHistoryService {

    @Resource
    private UserBrowseHistoryMapper userBrowseHistoryMapper;

    /**
     * 保存或更新浏览历史
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveOrUpdateHistory(Long uid, Integer vid, Integer progress) {
        LambdaQueryWrapper<UserBrowseHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBrowseHistory::getUid, uid)
                .eq(UserBrowseHistory::getVid, vid);
        
        UserBrowseHistory existing = userBrowseHistoryMapper.selectOne(queryWrapper);
        
        if (existing != null) {
            existing.setBrowseTime(LocalDateTime.now());
            existing.setProgress(progress);
            return userBrowseHistoryMapper.updateById(existing);
        } else {
            UserBrowseHistory history = new UserBrowseHistory();
            history.setUid(uid);
            history.setVid(vid);
            history.setBrowseTime(LocalDateTime.now());
            history.setProgress(progress);
            history.setCreateDate(LocalDateTime.now());
            return userBrowseHistoryMapper.insert(history);
        }
    }

    /**
     * 分页获取用户浏览历史列表(包含视频信息)
     */
    @Override
    public List<HistoryVideoDTO> getHistoryPageList(Integer uid, Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        return userBrowseHistoryMapper.selectHistoryWithVideo(uid, offset, pageSize);
    }

    /**
     * 获取用户指定视频的浏览进度
     */
    @Override
    public UserBrowseHistory getHistoryByUidAndVid(Integer uid, Integer vid) {
        LambdaQueryWrapper<UserBrowseHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserBrowseHistory::getUid, uid)
                .eq(UserBrowseHistory::getVid, vid);
        return userBrowseHistoryMapper.selectOne(queryWrapper);
    }
}