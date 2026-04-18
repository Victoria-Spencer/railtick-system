package org.rail.common.redis.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.redis.core.RedisCache;
import org.rail.common.redis.core.RedisStrategyCache;
import org.rail.common.redis.exception.CacheException;
import org.rail.common.redis.result.RedisData;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rail.common.redis.constant.RedisConstants.*;

/**
 * 三大缓存策略
 */
@Slf4j
@Component
public class RedissonStrategyCache implements RedisStrategyCache {

    @Autowired
    private RedisCache redisCache;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private ExecutorService cacheRebuildExecutor;


    private final Integer DEFAULT_RETRY_COUNT = 5; // 默认重试次数
    private final Integer RETRY_INTERVAL = 50; // 重试间隔（毫秒）

    // ========================== 极简通用校验 ================================
    private static void validateKey(String key) {
        if (StrUtil.isBlank(key)) throw new IllegalArgumentException("缓存Key不能为空");
    }
    private static void validateRequired(Object param, String name) {
        if (param == null) throw new IllegalArgumentException(StrUtil.format("参数【{}】不能为空", name));
    }
    private static <T> void validateCollectionNotEmpty(Collection<T> coll) {
        if (coll == null || coll.isEmpty()) throw new IllegalArgumentException("集合参数不能为空");
    }
    private static void validateTimeParams(Long time, TimeUnit timeUnit) {
        if (time == null || timeUnit == null || time <= 0) throw new IllegalArgumentException("缓存时间必须大于0");
    }
    private static void validateRetryCount(int retryCount) {
        if (retryCount <= 0) throw new IllegalArgumentException("重试次数必须大于0");
    }

    // ============================= 极简组合校验 ====================================
    private static <DTO> void validateCacheCommon(Function<DTO, String> keyGenerator, DTO dto, TypeReference<?> typeRef, Function<?, ?> dbFallback, Long time, TimeUnit timeUnit) {
        validateRequired(keyGenerator, "keyGenerator");
        validateRequired(dto, "dto");
        validateRequired(typeRef, "typeRef");
        validateRequired(dbFallback, "dbFallback");
        validateTimeParams(time, timeUnit);
    }
    private static <DTO> void validateCacheBatch(Function<DTO, String> keyGenerator, List<DTO> dtos, TypeReference<?> typeRef, Function<?, ?> batchDbFallback, Long time, TimeUnit timeUnit) {
        validateRequired(keyGenerator, "keyGenerator");
        validateCollectionNotEmpty(dtos);
        validateRequired(typeRef, "typeRef");
        validateRequired(batchDbFallback, "batchDbFallback");
        validateTimeParams(time, timeUnit);
    }
    private void validateCacheAsync() {
        validateRequired(cacheRebuildExecutor, "cacheRebuildExecutor");
    }

    // ========================== 缓存穿透 ================================
    /**
     * 缓存穿透（keyPrefix + id 生成Key）
     */
    @Override
    public <D, ID> D queryWithPassThrough(
            String keyPrefix,
            ID id,
            TypeReference<D> typeRef,
            Function<ID, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        return queryWithPassThrough(
                (d) -> keyPrefix + d,
                id,
                typeRef,
                dbFallback,
                time,
                timeUnit
        );
    }

    /**
     * 缓存穿透（自定义Key生成器）
     */
    @Override
    public <D, DTO> D queryWithPassThrough(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        validateCacheCommon(keyGenerator, dto, typeRef, dbFallback, time, timeUnit);
        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        // 查询缓存
        D data = redisCache.get(key);
        if (data != null) {
            log.debug("缓存命中 key:{}", key);
            return data;
        }
        if (redisCache.exists(key)) return null;

        // 缓存未命中，查询数据库
        data = dbFallback.apply(dto);

        // 数据库无数据，缓存空值
        if (data == null) {
            redisCache.set(key, (D) "", REDIS_CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 数据库有数据，写入缓存
        redisCache.set(key, data, time, timeUnit);
        return data;
    }

    /**
     * 批量缓存穿透
     */
    @Override
    public <D, DTO> Map<DTO, D> batchQueryWithPassThrough(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        validateCacheBatch(keyGenerator, dtos, typeRef, batchDbFallback, time, timeUnit);

        // 1. 构建DTO-Key映射
        Map<DTO, String> dtoKeyMap = new HashMap<>();
        List<String> keys = new ArrayList<>();
        for (DTO dto : dtos) {
            String key = keyGenerator.apply(dto);
            if (StrUtil.isBlank(key)) {
                throw new IllegalArgumentException("DTO生成的缓存Key不能为空：" + dto);
            }
            dtoKeyMap.put(dto, key);
            keys.add(key);
        }

        // 2. 批量查询缓存
        Map<String, D> cacheMap = redisCache.batchGet(keys);

        // 3. 筛选未命中的DTO
        List<DTO> missDtos = dtos.stream()
                .filter(dto -> !cacheMap.containsKey(dtoKeyMap.get(dto)))
                .collect(Collectors.toList());

        if (CollectionUtil.isEmpty(missDtos)) {
            log.debug("批量缓存穿透策略-全部命中缓存，命中数:{}", dtos.size());
            return buildResultMap(dtos, dtoKeyMap, cacheMap);
        }

        // 4. 批量查询数据库
        log.debug("批量缓存穿透策略-缓存未命中数:{}，查询数据库", missDtos.size());
        Map<DTO, D> dbMap = batchDbFallback.apply(missDtos);

        // 5. 批量写入缓存（空值+正常数据）
        Map<String, D> writeMap = new HashMap<>();
        for (DTO dto : missDtos) {
            String key = dtoKeyMap.get(dto);
            D data = dbMap.get(dto);
            if (data == null) {
                writeMap.put(key, (D) "");
            } else {
                writeMap.put(key, data);
            }
        }

        // 批量设置：空值用短TTL，正常数据用业务TTL
        Map<String, D> nullMap = new HashMap<>();
        Map<String, D> normalMap = new HashMap<>();
        writeMap.forEach((k, v) -> {
            if ("".equals(v)) {
                nullMap.put(k, v);
            } else {
                normalMap.put(k, v);
            }
        });

        if (MapUtil.isNotEmpty(nullMap)) {
            redisCache.batchSet(nullMap, REDIS_CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        if (MapUtil.isNotEmpty(normalMap)) {
            redisCache.batchSet(normalMap, time, timeUnit);
        }

        // 合并缓存结果
        cacheMap.putAll(writeMap);
        // 6. 组装结果
        return buildResultMap(dtos, dtoKeyMap, cacheMap);
    }

    // ========================== 互斥锁（Mutex）=========================

    /**
     * 互斥锁（简化版：keyPrefix + id，默认重试次数，仅 TypeReference）
     */
    @Override
    public <D, ID> D queryWithMutex(
            String keyPrefix,
            ID id,
            TypeReference<D> typeRef,
            Function<ID, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        return queryWithMutex(
                (d) -> keyPrefix + d,
                id,
                typeRef,
                dbFallback,
                time,
                timeUnit,
                DEFAULT_RETRY_COUNT
        );
    }

    /**
     * 互斥锁（自定义Key生成器，指定重试次数）
     */
    @Override
    public <D, DTO> D queryWithMutex(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    ) {
        validateCacheCommon(keyGenerator, dto, typeRef, dbFallback, time, timeUnit);
        validateRetryCount(retryCount);

        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        D data = redisCache.get(key);

        if (data != null) return data;
        if (redisCache.exists(key)) return null;

        // 缓存未命中，加锁重建
        String lockKey = REDIS_LOCK_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(0, REDIS_LOCK_TTL, TimeUnit.SECONDS);
            if (!locked) {
                Thread.sleep(RETRY_INTERVAL);
                return queryWithMutex(keyGenerator, dto, typeRef, dbFallback, time, timeUnit, retryCount - 1);
            }

            // 二次检查
            data = redisCache.get(key);
            if (data != null) return data;
            if (redisCache.exists(key)) return null;

            D dbData = dbFallback.apply(dto);
            if (dbData == null) {
                redisCache.set(key, (D) "", REDIS_CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            redisCache.set(key, dbData, time, timeUnit);
            return dbData;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 批量互斥锁（纯 Redisson 极简版）
     */
    @Override
    public <D, DTO> Map<DTO, D> batchQueryWithMutex(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    ) {
        validateCacheBatch(keyGenerator, dtos, typeRef, batchDbFallback, time, timeUnit);
        validateRetryCount(retryCount);

        // 1. 构建DTO-Key映射
        Map<DTO, String> dtoKeyMap = new HashMap<>();
        List<String> keys = new ArrayList<>();
        for (DTO dto : dtos) {
            String key = keyGenerator.apply(dto);
            if (StrUtil.isBlank(key)) {
                throw new IllegalArgumentException("DTO生成的缓存Key不能为空：" + dto);
            }
            dtoKeyMap.put(dto, key);
            keys.add(key);
        }

        // 2. 批量查询缓存
        Map<String, D> cacheMap = redisCache.batchGet(keys);
        // 3. 筛选未命中数据
        List<DTO> missDtos = dtos.stream()
                .filter(dto -> !cacheMap.containsKey(dtoKeyMap.get(dto)))
                .collect(Collectors.toList());

        if (CollectionUtil.isEmpty(missDtos)) {
            return buildResultMap(dtos, dtoKeyMap, cacheMap);
        }

        // 锁等待时间
        final long LOCK_WAIT_TIME = 2;
        Map<DTO, RLock> lockMap = new HashMap<>();
        try {
            // 4. 极简批量加锁（纯 Redisson + 重试机制）
            boolean allLocked = false;
            int currentRetry = Math.max(retryCount, 1);
            while (currentRetry-- > 0 && !allLocked) {
                allLocked = true;
                lockMap.clear();
                // 遍历加锁
                for (DTO dto : missDtos) {
                    RLock lock = redissonClient.getLock(REDIS_LOCK_PREFIX + dtoKeyMap.get(dto));
                    try {
                        // Redisson 原生 tryLock：等待时间、锁过期时间、单位
                        if (!lock.tryLock(LOCK_WAIT_TIME, REDIS_LOCK_TTL, TimeUnit.SECONDS)) {
                            allLocked = false;
                            break;
                        }
                        lockMap.put(dto, lock);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("批量加锁被中断", e);
                    }
                }
                // 加锁失败 → 释放已获取的锁 → 重试
                if (!allLocked) {
                    lockMap.values().forEach(RLock::unlock);
                    Thread.sleep(RETRY_INTERVAL);
                }
            }

            if (!allLocked) {
                throw new CacheException("批量获取锁失败，重试次数已耗尽");
            }

            // 5. 二次检查缓存（封装好的 batchGet）
            Map<String, D> doubleCheckMap = redisCache.batchGet(missDtos.stream().map(dtoKeyMap::get).toList());
            cacheMap.putAll(doubleCheckMap);

            // 6. 最终未命中 → 查库 + 批量写缓存
            List<DTO> finalMiss = missDtos.stream()
                    .filter(dto -> !cacheMap.containsKey(dtoKeyMap.get(dto)))
                    .toList();

            if (CollectionUtil.isNotEmpty(finalMiss)) {
                Map<DTO, D> dbMap = batchDbFallback.apply(finalMiss);
                Map<String, D> writeMap = new HashMap<>();
                for (DTO dto : finalMiss) {
                    D data = dbMap.get(dto);
                    writeMap.put(dtoKeyMap.get(dto), data == null ? (D) "" : data);
                }
                // 批量写入缓存
                redisCache.batchSet(writeMap, time, timeUnit);
                cacheMap.putAll(writeMap);
            }

            return buildResultMap(dtos, dtoKeyMap, cacheMap);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("批量互斥锁执行异常", e);
        } finally {
            // 7. 统一释放锁
            lockMap.values().forEach(lock -> {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            });
        }
    }


    // =========================== 逻辑过期（LogicalExpire）=============================

    /**
     * 逻辑过期（简化版：keyPrefix + id）
     */
    @Override
    public <D, ID> D queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            TypeReference<D> typeRef,
            Function<ID, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        return queryWithLogicalExpire(
                (d) -> keyPrefix + d,
                id,
                typeRef,
                dbFallback,
                time,
                timeUnit
        );
    }

    /**
     * 逻辑过期（核心版：自定义Key生成器）
     */
    @Override
    public <D, DTO> D queryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        validateCacheCommon(keyGenerator, dto, typeRef, dbFallback, time, timeUnit);

        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        RedisData<D> redisData = redisCache.get(key);

        if (redisData == null || redisData.getData() == null) {
            D dbData = dbFallback.apply(dto);
            if (dbData != null) redisCache.setWithLogicalExpire(key, dbData, time, timeUnit);
            return dbData;
        }

        D data = redisData.getData();
        // 缓存未过期，直接返回
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) return data;

        String lockKey = REDIS_LOCK_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        // 缓存过期，加锁，异步重建
        try {
            if(lock.tryLock(2, TimeUnit.SECONDS)) {
                validateCacheAsync();
                cacheRebuildExecutor.submit(() -> {
                    try {
                        D dbData = dbFallback.apply(dto);
                        if (dbData != null) {
                            redisCache.setWithLogicalExpire(key, dbData, time, timeUnit);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("逻辑过期策略-异步重建缓存失败", e);
                    } finally {
                        lock.unlock();
                    }
                });
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("获取锁失败", e);
        }

        // 返回旧数据
        return data;
    }

    /**
     * 批量逻辑过期
     */
    @Override
    public <D, DTO> Map<DTO, D> batchQueryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        validateCacheBatch(keyGenerator, dtos, typeRef, batchDbFallback, time, timeUnit);

        // 构建DTO-Key映射
        Map<DTO, String> dtoKeyMap = new HashMap<>();
        List<String> keys = new ArrayList<>();
        for (DTO dto : dtos) {
            String key = keyGenerator.apply(dto);
            if (StrUtil.isBlank(key)) {
                throw new IllegalArgumentException("DTO生成的缓存Key不能为空：" + dto);
            }
            dtoKeyMap.put(dto, key);
            keys.add(key);
        }

        // 结果集合
        Map<DTO, D> resultMap = new LinkedHashMap<>();
        List<DTO> missDtos = new ArrayList<>();
        List<DTO> expiredDtos = new ArrayList<>();

        Map<String, RedisData<D>> cacheMap = redisCache.batchGet(keys);

        // 遍历处理缓存数据
        for (DTO dto : dtos) {
            RedisData<D> redisData = cacheMap.get(dtoKeyMap.get(dto));
            // 缓存未命中
            if (redisData == null || redisData.getData() == null) {
                missDtos.add(dto);
                continue;
            }
            // 判断缓存是否过期
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                resultMap.put(dto, redisData.getData());
            } else {
                // 已过期：返回旧数据，标记异步重建
                resultMap.put(dto, redisData.getData());
                expiredDtos.add(dto);
            }
        }

        // 处理未命中的DTO（同步查库）
        if (!CollectionUtil.isEmpty(missDtos)) {
            log.debug("批量逻辑过期策略-未命中数:{}，同步查询数据库", missDtos.size());
            Map<DTO, D> missDbMap = batchDbFallback.apply(missDtos);
            for (DTO dto : missDtos) {
                D data = missDbMap.getOrDefault(dto, null);
                resultMap.put(dto, data);
                if (data != null) {
                    redisCache.setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
                }
            }
        }

        // 处理过期的DTO（异步重建）
        if (CollectionUtil.isNotEmpty(expiredDtos)) {
            rebuildExpiredBatchCache(expiredDtos, dtoKeyMap, batchDbFallback, time, timeUnit);
        }

        return resultMap;
    }

    /**
     * 构建结果Map
     */
    private <DTO, D> Map<DTO, D> buildResultMap(List<DTO> dtos, Map<DTO, String> dtoKeyMap, Map<String, D> cacheMap) {
        return dtos.stream().collect(Collectors.toMap(
                Function.identity(),
                dto -> cacheMap.getOrDefault(dtoKeyMap.get(dto), null),
                (oldValue, newValue) -> oldValue,
                LinkedHashMap::new
        ));
    }

    /**
     * 批量重建过期缓存
     */
    private <D, DTO> void rebuildExpiredBatchCache(
            List<DTO> expiredDtos,
            Map<DTO, String> dtoKeyMap,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        try {
            // 线程池异步重建缓存
            validateCacheAsync();
            cacheRebuildExecutor.submit(() -> {
                // 1. 二次检查缓存
                List<String> checkKeys = expiredDtos.stream()
                        .map(dtoKeyMap::get)
                        .toList();
                Map<String, RedisData<D>> doubleCheckMap = redisCache.batchGet(checkKeys);

                // 2. 筛选真正需要重建的数据
                List<DTO> finalRebuildDtos = expiredDtos.stream()
                        .filter(dto -> {
                            RedisData<D> redisData = doubleCheckMap.get(dtoKeyMap.get(dto));
                            return redisData == null || redisData.getExpireTime().isBefore(LocalDateTime.now());
                        })
                        .toList();

                if (CollectionUtil.isEmpty(finalRebuildDtos)) {
                    return;
                }

                // 3. 批量查库 + 写入逻辑过期缓存
                log.debug("批量逻辑过期策略-异步重建缓存，重建数:{}", finalRebuildDtos.size());
                Map<DTO, D> dbMap = batchDbFallback.apply(finalRebuildDtos);
                for (DTO dto : finalRebuildDtos) {
                    D data = dbMap.get(dto);
                    if (data != null) {
                        redisCache.setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池拒绝，同步兜底重建
            log.warn("缓存重建线程池已满，同步重建过期缓存");
            Map<DTO, D> dbMap = batchDbFallback.apply(expiredDtos);
            for (DTO dto : expiredDtos) {
                D data = dbMap.get(dto);
                if (data != null) {
                    redisCache.setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
                }
            }
        }
    }
}
