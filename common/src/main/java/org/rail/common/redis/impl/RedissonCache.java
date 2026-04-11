package org.rail.common.redis.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import org.rail.common.redis.core.RedisCache;
import org.rail.common.redis.exception.CacheException;
import org.rail.common.redis.result.RedisData;
import org.redisson.api.*;
import org.redisson.client.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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
     * 单条获取缓存，支持传入类型
     */
    @Override
    public <T> T get(String key, Class<T> type) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        try {
            if (type == byte[].class) {
                RBucket<byte[]> bucket = redissonClient.getBucket(key, ByteArrayCodec.INSTANCE);
                byte[] data = bucket.get();
                return data == null || data.length == 0 ? null : (T) data;
            } else {
                RBucket<T> bucket = redissonClient.getBucket(key);
                T data = bucket.get();
                if ("".equals(data)) {
                    return null;
                }
                return data;
            }
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
            return buckets.get(keyArray);
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
    public <T> void addSetMemberWithExpire(String key, T value, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || value == null || expireTime == null || expireTime <= 0) {
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
    public <T> void addSetMembersWithExpire(String key, Collection<T> values, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || CollectionUtil.isEmpty(values) || expireTime == null || expireTime <= 0) {
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

    // =============================== Bitmap 操作实现 ===============================

    /**
     * 设置Bitmap指定偏移量的值
     */
    @Override
    public void setBit(String key, long offset, boolean value) {
        if (StrUtil.isBlank(key)) {
            return;
        }
        try {
            RBitSet bitSet = redissonClient.getBitSet(key);
            bitSet.set(offset, value);
        } catch (Exception e) {
            throw new CacheException("Bitmap设置位失败", e);
        }
    }

    /**
     * 获取Bitmap指定偏移量的值
     */
    @Override
    public boolean getBit(String key, long offset) {
        if (StrUtil.isBlank(key)) {
            return false;
        }
        try {
            RBitSet bitSet = redissonClient.getBitSet(key);
            return bitSet.get(offset);
        } catch (Exception e) {
            throw new CacheException("Bitmap获取位失败", e);
        }
    }

    /**
     * 统计Bitmap中1的数量
     */
    @Override
    public long bitCount(String key) {
        if (StrUtil.isBlank(key)) {
            return 0;
        }
        try {
            RBitSet bitSet = redissonClient.getBitSet(key);
            return bitSet.cardinality();
        } catch (Exception e) {
            throw new CacheException("Bitmap统计位数失败", e);
        }
    }

    /**
     * 批量设置Bitmap多个偏移量为指定值
     */
    @Override
    public void batchSetBits(String key, Collection<Long> offsets, boolean value) {
        if (StrUtil.isBlank(key) || CollectionUtil.isEmpty(offsets)) {
            return;
        }
        try {
            RBitSet bitSet = redissonClient.getBitSet(key);
            offsets.forEach(offset -> bitSet.set(offset, value));
        } catch (Exception e) {
            throw new CacheException("Bitmap批量设置位失败", e);
        }
    }

    /**
     * 设置Bitmap指定区间 [startOffset, endOffset) 所有位为指定值
     * 注意：RBitSet的set/clear是左闭右开区间
     */
    @Override
    public void setRangeBits(String key, long startOffset, long endOffset, boolean value) {
        if (StrUtil.isBlank(key) || startOffset < 0 || endOffset < startOffset) {
            return;
        }
        try {
            RBitSet bitSet = redissonClient.getBitSet(key);
            if (value) {
                bitSet.set(startOffset, endOffset);
            } else {
                bitSet.clear(startOffset, endOffset);
            }
        } catch (Exception e) {
            throw new CacheException("Bitmap区间批量设置位失败", e);
        }
    }

    /**
     * 清空整个Bitmap（删除key）
     */
    @Override
    public void clearBitmap(String key) {
        delete(key);
    }

    /**
     * 判断Bitmap指定区间[startOffset, endOffset)（左闭右开）是否全为0
     * 统计区间内1的数量，数量为0则代表全0
     */
    @Override
    public boolean isRangeAllZero(String key, long startOffset, long endOffset) {
        if (StrUtil.isBlank(key) || startOffset < 0 || endOffset < startOffset) {
            return false;
        }
        try {
            RBitSet bitSet = redissonClient.getBitSet(key);
            long[] indexes = LongStream.range(startOffset, endOffset).toArray();
            boolean[] bits = bitSet.get(indexes);

            for (boolean bit : bits) {
                if (bit) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            throw new CacheException("Bitmap区间全0判断失败", e);
        }
    }

    // =============================== Hash 操作实现 =================================

    /**
     * Hash 存入单个字段
     */
    @Override
    public <T> void hPut(String key, String hashKey, T value) {
        if (StrUtil.isBlank(key) || StrUtil.isBlank(hashKey)) {
            return;
        }
        try {
            RMap<String, T> map = redissonClient.getMap(key);
            map.put(hashKey, value == null ? (T) "" : value);
        } catch (Exception e) {
            throw new CacheException("Hash存入单个字段失败", e);
        }
    }

    /**
     * Hash 存入单个字段（带独立过期时间）
     * 效果：仅当前hashKey到期删除，其他field正常保留
     */
    @Override
    public <T> void hPut(String key, String hashKey, T value, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || StrUtil.isBlank(hashKey)) {
            return;
        }
        try {
            // 用RMapCache替代普通RMap，支持单个entry设置TTL
            RMapCache<String, T> mapCache = redissonClient.getMapCache(key);
            mapCache.put(
                    hashKey,
                    value == null ? (T) "" : value,
                    expireTime,
                    timeUnit
            );
        } catch (Exception e) {
            throw new CacheException("Hash单个字段带过期时间存入失败", e);
        }
    }

    /**
     * Hash 批量存入字段
     */
    @Override
    public <T> void hPutAll(String key, Map<String, T> map) {
        if (StrUtil.isBlank(key) || MapUtil.isEmpty(map)) {
            return;
        }
        try {
            RMap<String, T> rMap = redissonClient.getMap(key);
            Map<String, T> finalMap = handleNullValue(map);
            rMap.putAll(finalMap);
        } catch (Exception e) {
            throw new CacheException("Hash批量存入字段失败", e);
        }
    }

    /**
     * Hash 批量存入字段（带过期时间）
     */
    @Override
    public <T> void hPutAll(String key, Map<String, T> map, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || MapUtil.isEmpty(map)) {
            return;
        }
        try {
            RMapCache<String, T> mapCache = redissonClient.getMapCache(key);
            Map<String, T> finalMap = handleNullValue(map);
            mapCache.putAll(finalMap, expireTime, timeUnit);
        } catch (Exception e) {
            throw new CacheException("Hash批量带过期时间存入失败", e);
        }
    }

    /**
     * Hash批量存入字段 + 给【整个Hash】设置过期时间
     * 效果：到期后 → 整个Hash被删除，所有字段全部清空
     */
    @Override
    public <T> void hPutAllWholeExpire(String key, Map<String, T> map, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key) || MapUtil.isEmpty(map)) {
            return;
        }
        try {
            RMap<String, T> rMap = redissonClient.getMap(key);
            Map<String, T> finalMap = handleNullValue(map);
            rMap.putAll(finalMap);

            if (expireTime != null && expireTime > 0 && timeUnit != null) {
                rMap.expire(expireTime, timeUnit);
            }
        } catch (Exception e) {
            throw new CacheException("Hash批量存入+整体过期设置失败", e);
        }
    }

    /**
     * Hash 获取单个字段
     */
    @Override
    public <T> T hGet(String key, String hashKey) {
        if (StrUtil.isBlank(key) || StrUtil.isBlank(hashKey)) {
            return null;
        }
        try {
            RMap<String, T> map = redissonClient.getMap(key);
            T data = map.get(hashKey);
            // 空字符串转回null
            return "".equals(data) ? null : data;
        } catch (Exception e) {
            throw new CacheException("Hash获取单个字段失败", e);
        }
    }

    /**
     * Hash 批量获取多个字段
     */
    @Override
    public <T> List<T> hMultiGet(String key, Collection<String> hashKeys) {
        if (StrUtil.isBlank(key) || CollectionUtil.isEmpty(hashKeys)) {
            return Collections.emptyList();
        }
        try {
            RMap<String, T> map = redissonClient.getMap(key);
            Set<String> keySet = new HashSet<>(hashKeys);
            Map<String, T> multiGet = map.getAll(keySet);
            return multiGet.values().stream()
                    .map(v -> "".equals(v) ? null : v)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new CacheException("Hash批量获取字段失败", e);
        }
    }

    /**
     * Hash 获取所有字段和值
     */
    @Override
    public <T> Map<String, T> hEntries(String key) {
        if (StrUtil.isBlank(key)) {
            return Collections.emptyMap();
        }
        try {
            RMap<String, T> map = redissonClient.getMap(key);
            // 处理空值
            return map.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> "".equals(e.getValue()) ? null : e.getValue()
                    ));
        } catch (Exception e) {
            throw new CacheException("Hash获取所有键值对失败", e);
        }
    }

    /**
     * Hash 获取所有字段名
     */
    @Override
    public Set<String> hKeys(String key) {
        if (StrUtil.isBlank(key)) {
            return Collections.emptySet();
        }
        try {
            RMap<String, Object> map = redissonClient.getMap(key);
            return map.keySet();
        } catch (Exception e) {
            throw new CacheException("Hash获取所有字段名失败", e);
        }
    }

    /**
     * Hash 获取所有字段值
     */
    @Override
    public <T> List<T> hValues(String key) {
        if (StrUtil.isBlank(key)) {
            return Collections.emptyList();
        }
        try {
            RMap<String, T> map = redissonClient.getMap(key);
            // 处理空值
            return map.values().stream()
                    .map(v -> "".equals(v) ? null : v)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new CacheException("Hash获取所有值失败", e);
        }
    }

    /**
     * Hash 删除指定字段
     */
    @Override
    public Long hDelete(String key, String... hashKeys) {
        if (StrUtil.isBlank(key) || hashKeys == null || hashKeys.length == 0) {
            return 0L;
        }
        try {
            RMap<String, Object> map = redissonClient.getMap(key);
            return map.fastRemove(hashKeys);
        } catch (Exception e) {
            throw new CacheException("Hash删除字段失败", e);
        }
    }

    /**
     * 判断 Hash 中是否存在指定字段
     */
    @Override
    public Boolean hExists(String key, String hashKey) {
        if (StrUtil.isBlank(key) || StrUtil.isBlank(hashKey)) {
            return false;
        }
        try {
            RMap<String, Object> map = redissonClient.getMap(key);
            return map.containsKey(hashKey);
        } catch (Exception e) {
            throw new CacheException("Hash判断字段是否存在失败", e);
        }
    }

    /**
     * Hash 字段数值自增/自减
     */
    @Override
    public Long hIncr(String key, String hashKey, long delta) {
        if (StrUtil.isBlank(key) || StrUtil.isBlank(hashKey)) {
            return 0L;
        }
        try {
            RMap<String, Long> map = redissonClient.getMap(key);
            return map.addAndGet(hashKey, delta);
        } catch (Exception e) {
            throw new CacheException("Hash字段自增失败", e);
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
