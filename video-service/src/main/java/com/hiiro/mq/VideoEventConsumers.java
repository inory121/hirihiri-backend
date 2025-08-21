package com.hiiro.mq;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiiro.entity.Video;
import com.hiiro.entity.document.VideoDocument;
import com.hiiro.mapper.VideoMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
public class VideoEventConsumers {

    @Resource
    private ElasticsearchOperations esOperations;
    @Resource
    private VideoMapper videoMapper;

    @Bean
    public Consumer<Map<String, Object>> videoEventConsumer() {
        return event -> {
            try {
                String type = String.valueOf(event.getOrDefault("type", ""));
                Long vid = event.get("vid") == null ? null : Long.valueOf(String.valueOf(event.get("vid")));
                if (vid == null) {
                    log.warn("[video.events] 无效的vid, event={}", event);
                    return;
                }
                switch (type) {
                    case "created":
                    case "updated": {
                        Video dbVideo = videoMapper.selectOne(new LambdaQueryWrapper<Video>().eq(Video::getVid, vid));
                        if (dbVideo == null) {
                            log.warn("[video.events] 未找到视频, vid={}", vid);
                            return;
                        }
                        VideoDocument doc = BeanUtil.copyProperties(dbVideo, VideoDocument.class);
                        esOperations.save(doc);
                        log.info("[video.events] 已同步ES, type={}, vid={}", type, vid);
                        break;
                    }
                    case "deleted": {
                        esOperations.delete(String.valueOf(vid), VideoDocument.class);
                        log.info("[video.events] 已从ES删除, vid={}", vid);
                        break;
                    }
                    default:
                        log.warn("[video.events] 未知事件类型: {}, event={}", type, event);
                }
            } catch (Exception e) {
                log.error("[video.events] 消费失败, event={}", event, e);
                throw e;
            }
        };
    }
} 