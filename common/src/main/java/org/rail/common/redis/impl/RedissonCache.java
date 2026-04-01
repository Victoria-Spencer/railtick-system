package org.rail.common.redis.impl;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import org.rail.common.redis.core.RedisCache;
import org.rail.common.redis.exception.CacheException;
import org.rail.common.redis.result.RedisData;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基础操作
 */
@Component
public class RedissonCache implements RedisCache {

    @Autowired
    private RedissonClient redissonClient;

    // =============================== String类型缓存操作封装 ===================================

    /**
     * 设置缓存
     */
    @Override
    public <T> void set(String key, T value) {
        if (StrUtil.isBlank(key)) return;
        try {
            RBucket<T> bucket = redissonClient.getBucket(key);
            bucket.set(value == null ? (T) "" : value);
        } catch (Exception e) {
            throw new CacheException("缓存设置失败", e);
        }
    }

    @Override
    public <T> void set(String key, T value, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key)) return;
        try {
            RBucket<T> bucket = redissonClient.getBucket(key);
            bucket.set(value == null ? (T) "" : value, expireTime, timeUnit);
        } catch (Exception e) {
            throw new CacheException("缓存设置失败", e);
        }
    }

    /**
     * 设置逻辑过期缓存
     */
    @Override
    public <T> void setWithLogicalExpire(String key, T value, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || value == null) return;
        try {
            RedisData<T> redisData = new RedisData<>();
            redisData.setData(value);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
            RBucket<RedisData<T>> bucket = redissonClient.getBucket(key);
            bucket.set(redisData);
        } catch (Exception e) {
            throw new CacheException("逻辑过期缓存设置失败", e);
        }
    }

    /**
     * 批量设置缓存（无过期时间）
     */
    @Override
    public <T> void batchSet(Map<String, T> keyValueMap) {
        if (MapUtil.isEmpty(keyValueMap)) {
            return;
        }
        try {
            RBuckets buckets = redissonClient.getBuckets();
            // 统一处理null值为""，和单条set逻辑保持一致
            Map<String, T> finalMap = handleNullValue(keyValueMap);
            buckets.set(finalMap);
        } catch (Exception e) {
            throw new CacheException("批量缓存设置失败", e);
        }
    }

    /**
     * 批量设置缓存（带统一过期时间）
     * 与单条set逻辑完全一致：空值转""、过滤空key、统一异常
     * 用RBatch批量执行，仅1次网络往返，性能远高于循环单个set
     */
    @Override
    public <T> void batchSet(Map<String, T> keyValueMap, Long expireTime, TimeUnit timeUnit) {
        // 防御性校验：空Map、无效过期时间直接返回
        if (MapUtil.isEmpty(keyValueMap) || expireTime == null || expireTime <= 0 || timeUnit == null) {
            return;
        }
        try {
            // 1. 预处理：空值转""、过滤空key，和单条set逻辑100%一致
            Map<String, T> finalMap = handleNullValue(keyValueMap);
            if (MapUtil.isEmpty(finalMap)) {
                return;
            }

            // 2. 创建RBatch批量操作对象（一次性打包所有命令，仅1次网络IO）
            RBatch batch = redissonClient.createBatch();

            // 3. 遍历Map，批量添加set命令（每个key独立设置过期时间，原子性保证）
            for (Map.Entry<String, T> entry : finalMap.entrySet()) {
                String key = entry.getKey();
                T value = entry.getValue();
                // 直接调用RBucket的setAsync，和单条set的逻辑完全一致
                batch.getBucket(key).setAsync(value, expireTime, timeUnit);
            }

            // 4. 执行批量命令（一次性发送所有请求到Redis，同步阻塞直到完成）
            batch.execute();
        } catch (Exception e) {
            throw new CacheException("批量缓存设置失败", e);
        }
    }

    /**
     * 单条获取缓存（自动处理空值"" -> null）
     */
    @Override
    public <T> T get(String key) {
        if (StrUtil.isBlank(key)) return null;
        try {
            RBucket<T> bucket = redissonClient.getBucket(key);
            T data = bucket.get();
            if ("".equals(data)) {
                return null;
            }
            return data;
        } catch (Exception e) {
            throw new CacheException("缓存获取失败", e);
        }
    }

    /**
     * 批量获取缓存
     * 底层：Redisson RBuckets 批量get，性能极高
     */
    @Override
    public <T> Map<String, T> batchGet(Collection<String> keys) {
        if (CollectionUtil.isEmpty(keys)) {
            return Collections.emptyMap();
        }
        try {
            // 统一过滤有效key
            List<String> validKeys = filterValidKeys(keys);
            if (CollectionUtil.isEmpty(validKeys)) {
                return Collections.emptyMap();
            }

            // 核心批量获取：List转数组适配API
            RBuckets buckets = redissonClient.getBuckets();
            String[] keyArray = validKeys.toArray(String[]::new);
            // 泛型返回，和单条get逻辑一致
            return buckets.<T>get(keyArray);
        } catch (Exception e) {
            throw new CacheException("批量缓存获取失败", e);
        }
    }

    /**
     * 统一处理null值：和单条set逻辑一致，value为null时转为空字符串
     */
    private <T> Map<String, T> handleNullValue(Map<String, T> keyValueMap) {
        return keyValueMap.entrySet().stream()
                .filter(entry -> StrUtil.isNotBlank(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? (T) "" : entry.getValue()
                ));
    }

    // =============================== Set类型缓存操作封装 ===================================
    /**
     * 向Set缓存添加单个成员（对应原stringRedisTemplate.opsForSet().add）
     */
    @Override
    public <T> void addSetMember(String key, T value) {
        if (StrUtil.isBlank(key) || value == null) {
            return;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.add(value);
        } catch (Exception e) {
            throw new CacheException("Set缓存添加成员失败", e);
        }
    }

    /**
     * 向Set缓存批量添加成员（对应原stringRedisTemplate.opsForSet().add(数组)）
     * 【核心特性：追加，不覆盖，自动去重】
     */
    @Override
    public <T> void addSetMembers(String key, Collection<T> values) {
        if (StrUtil.isBlank(key) || CollectionUtil.isEmpty(values)) {
            return;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.addAll(values);
        } catch (Exception e) {
            throw new CacheException("Set缓存批量添加成员失败", e);
        }
    }

    /**
     * 向Set缓存添加单个成员，并设置过期时间
     */
    @Override
    public <T> void addSetMemberWithExpire(String key, T value, long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || value == null || expireTime <= 0) {
            return;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.add(value);
            // 设置过期时间（自动刷新）
            set.expire(expireTime, timeUnit);
        } catch (Exception e) {
            throw new CacheException("Set缓存添加成员(带过期)失败", e);
        }
    }

    /**
     * 向Set缓存批量添加成员，并设置过期时间
     */
    @Override
    public <T> void addSetMembersWithExpire(String key, Collection<T> values, long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || CollectionUtil.isEmpty(values) || expireTime <= 0) {
            return;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.addAll(values);
            // 设置过期时间（自动刷新）
            set.expire(expireTime, timeUnit);
        } catch (Exception e) {
            throw new CacheException("Set缓存批量添加成员(带过期)失败", e);
        }
    }

    /**
     * 获取Set缓存所有成员（对应原stringRedisTemplate.opsForSet().members）
     */
    @Override
    public <T> Set<T> getSetMembers(String key) {
        if (StrUtil.isBlank(key)) {
            return Collections.emptySet();
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            return set.readAll();
        } catch (Exception e) {
            throw new CacheException("获取Set缓存成员失败", e);
        }
    }

    // ========================== 缓存删除封装（String、Set通用） =========================
    /**
     * 删除缓存
     */
    @Override
    public void delete(String key) {
        if (StrUtil.isBlank(key)) return;
        try {
            RBucket<Object> bucket = redissonClient.getBucket(key);
            bucket.delete();
        } catch (Exception e) {
            throw new CacheException("缓存删除失败", e);
        }
    }

    /**
     * 批量删除缓存
     * 底层用RBatch批量执行deleteAsync，仅1次网络往返，性能远高于循环单个删除
     */
    @Override
    public void batchDelete(Collection<String> keys) {
        if (CollectionUtil.isEmpty(keys)) return;
        try {
            // 过滤空key
            List<String> validKeys = filterValidKeys(keys);
            if (CollectionUtil.isEmpty(validKeys)) {
                return;
            }

            // 1. 创建RBatch批量操作对象
            RBatch batch = redissonClient.createBatch();

            // 2. 遍历有效key，批量添加deleteAsync命令
            for (String key : validKeys) {
                batch.getBucket(key).deleteAsync();
            }

            // 3. 执行批量命令（一次性发送所有请求，同步阻塞直到完成）
            batch.execute();
        } catch (Exception e) {
            throw new CacheException("批量缓存删除失败", e);
        }
    }

    /**
     * 判断缓存是否存在
     */
    @Override
    public boolean exists(String key) {
        if (StrUtil.isBlank(key)) {
            return false;
        }
        try {
            RBucket<Object> bucket = redissonClient.getBucket(key);
            return bucket.isExists();
        } catch (Exception e) {
            throw new CacheException("缓存存在性判断失败", e);
        }
    }

    /**
     * 统一过滤有效key：移除空/空白key，与handleNullValue逻辑对应
     */
    private List<String> filterValidKeys(Collection<String> keys) {
        if (CollectionUtil.isEmpty(keys)) {
            return Collections.emptyList();
        }
        return keys.stream()
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
