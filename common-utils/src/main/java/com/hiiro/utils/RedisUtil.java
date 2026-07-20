package com.hiiro.utils;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisUtil {

    // redis默认TTL设置为10天
    private static final long REDIS_DEFAULT_EXPIRE_TIME = 60 * 60 * 24 * 10;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置键值对，如果键不存在才设置（分布式锁）
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Redis setIfAbsent操作失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 设置键值对并指定过期时间
     */
    public boolean setWithExpire(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            return true;
        } catch (Exception e) {
            log.error("Redis set操作失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 设置指定 key 的值
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("Redis set操作失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 批量设置键值对，并指定过期时间
     */
    public <T> boolean setList(String key, List<T> list) {
        try {
            for (T t : list) {
                redisTemplate.opsForList().rightPushAll(key, t);
            }
            return true;
        } catch (Exception e) {
            log.error("Redis setList操作失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 获取指定 key 的值，并处理异常
     */
    public Optional<Object> get(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key));
        } catch (Exception e) {
            log.error("Redis get操作失败，key: {}", key, e);
            return Optional.empty();
        }
    }

    public <T> Optional<T> getObject(String key, Class<T> clazz) {
        try {
            return get(key).map(obj -> {
                try {
                    return JSON.parseObject(obj.toString(), clazz);
                } catch (Exception e) {
                    log.error("Redis getObject解析失败，key: {}，class: {}", key, clazz.getName(), e);
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("Redis getObject操作失败，key: {}，class: {}", key, clazz.getName(), e);
            return Optional.empty();
        }
    }

    /**
     * 获取列表指定索引范围的元素，并处理异常
     */
    public <T> List<T> getList(String key, long index, Class<T> clazz) {
        try {
            // 获取列表长度
            Long size = redisTemplate.opsForList().size(key);
            if (size == null || size <= 0) {
                return new ArrayList<>();
            }

            // 获取指定索引范围的元素
            List<Object> objects;
            if (index >= 0) {
                // 获取单个元素
                objects = Collections.singletonList(redisTemplate.opsForList().index(key, index));
            } else {
                // 获取所有元素 (index < 0)
                objects = redisTemplate.opsForList().range(key, 0, -1);
            }

            if (objects == null || objects.isEmpty()) {
                return new ArrayList<>();
            }

            // 将每个元素从JSON字符串解析为指定类型的对象
            List<T> result = new ArrayList<>();
            for (Object obj : objects) {
                if (obj instanceof String) {
                    T parsedObj = JSON.parseObject((String) obj, clazz);
                    result.add(parsedObj);
                } else if (obj != null) {
                    // 如果对象不是字符串，先转换为JSON字符串再解析
                    String jsonString = JSON.toJSONString(obj);
                    T parsedObj = JSON.parseObject(jsonString, clazz);
                    result.add(parsedObj);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Redis getList操作失败，key: {}，index: {}，class: {}", key, index, clazz.getName(), e);
            return new ArrayList<>();
        }
    }


    /**
     * 删除一个或多个key，并处理异常
     */
    public boolean delete(String... keys) {
        if (Objects.isNull(keys) || keys.length == 0) {
            return false;
        }

        try {
            Long deletedKeysCount = redisTemplate.delete(Arrays.asList(keys));
            return deletedKeysCount > 0;
        } catch (Exception e) {
            log.error("Redis delete操作失败，keys: {}", Arrays.toString(keys), e);
            return false;
        }
    }

    /**
     * 设置 key 的值，并设置过期时间，处理异常
     */
    public boolean setWithDefaultExpire(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value, REDIS_DEFAULT_EXPIRE_TIME, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("Redis setWithExpire操作失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 检查给定 key 是否存在，并处理异常
     */
    public boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.error("Redis hasKey操作失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 设置 key 的过期时间，并处理异常
     */
    public boolean setExpire(String key, long timeout, TimeUnit unit) {
        try {
            return redisTemplate.expire(key, timeout, unit);
        } catch (Exception e) {
            log.error("Redis setExpire操作失败，key: {}", key, e);
            return false;
        }
    }

    /**
     * 获取指定 key 的过期时间，并处理异常
     */
    public Optional<Long> getExpire(String key, TimeUnit unit) {
        try {
            Long expire = redisTemplate.getExpire(key, unit);
            return Optional.of(expire);
        } catch (Exception e) {
            log.error("Redis getExpire操作失败，key: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 递增指定的 key，并处理异常
     */
    public Optional<Long> increment(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, delta);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("Redis increment操作失败，key: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * 递减指定的 key，并处理异常
     */
    public Optional<Long> decrement(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().decrement(key, delta);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("Redis decrement操作失败，key: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * ZSet：给指定 member 的分数增加 delta
     */
    public Optional<Double> zIncrementScore(String key, Object value, double delta) {
        try {
            Double result = redisTemplate.opsForZSet().incrementScore(key, value, delta);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.error("Redis zIncrementScore操作失败，key: {}", key, e);
            return Optional.empty();
        }
    }

    /**
     * ZSet：按分数从高到低取前 N 个（带分数）
     */
    public Set<Object> zReverseRange(String key, long start, long end) {
        try {
            Set<Object> result = redisTemplate.opsForZSet().reverseRange(key, start, end);
            return result != null ? result : Collections.emptySet();
        } catch (Exception e) {
            log.error("Redis zReverseRange操作失败，key: {}", key, e);
            return Collections.emptySet();
        }
    }

}