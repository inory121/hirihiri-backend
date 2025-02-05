package com.hiiro.utils;

import cn.hutool.core.bean.BeanUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    // redis默认TTL设置为1小时
    private static final long REDIS_DEFAULT_EXPIRE_TIME = 60 * 60;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置指定 key 的值。
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 获取指定 key 的值。
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public <T> T getObject(String key, Class<T> clazz) {
        return BeanUtil.toBean(redisTemplate.opsForValue().get(key), clazz);
    }

    /**
     * 删除一个或多个key。
     */
    public void delete(String... keys) {
        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }

    /**
     * 设置 key 的值，并设置过期时间。
     */
    public void setWithExpire(String key, Object value, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, REDIS_DEFAULT_EXPIRE_TIME, unit);
    }

    /**
     * 检查给定 key 是否存在。
     */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置 key 的过期时间。
     */
    public boolean setExpire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取指定 key 的过期时间。
     */
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    /**
     * 递增指定的 key。
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 递减指定的 key。
     */
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }
}