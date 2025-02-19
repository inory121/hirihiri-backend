package com.hiiro.utils;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    public <T> Long setAllList(String key, List<T> list) {
        List<String> dataList = list.stream()
                .map(JSON::toJSONString)
                .collect(Collectors.toList());
        return redisTemplate.opsForList().rightPushAll(key, dataList);
    }

    /**
     * 获取指定 key 的值。
     */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public <T> T getObject(String key, Class<T> clazz) {
        return JSON.parseObject((String) redisTemplate.opsForValue().get(key), clazz);
    }

    public <T> List<T> getList(String key,long index, Class<T> clazz) {
        Object object = redisTemplate.opsForList().index(key,index);
        if (Objects.nonNull(object)) {
            return JSON.parseArray(object.toString(),clazz);
        }
        throw new RuntimeException("redis key is null");
    }
    /**
     * 删除一个或多个key。
     */
    public Boolean delete(String... keys) {
        if (Objects.isNull(keys) || keys.length == 0) {
            return false;
        }

        Long deletedKeysCount = redisTemplate.delete(Arrays.asList(keys));
        return deletedKeysCount > 0;
    }

    /**
     * 设置 key 的值，并设置过期时间。
     */
    public void setWithExpire(String key, Object value, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, REDIS_DEFAULT_EXPIRE_TIME, unit);
    }

    /**
     * 设置 key 的值，并设置过期时间。
     */
    public void setObjectWithExpire(String key, Object value, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, JSON.toJSONString(value), REDIS_DEFAULT_EXPIRE_TIME, unit);
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