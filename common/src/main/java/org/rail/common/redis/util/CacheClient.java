package org.rail.common.redis.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.redis.bloomfilter.DistributedBloomFilterManager;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;
import org.rail.common.core.result.PageResult;
import org.rail.common.redis.result.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rail.common.redis.constant.RedisConstants.AGG_CACHE_ORDER_BIZ_TYPE;

/**
 * 缓存客户端工具类（纯 TypeReference 版）
 * 特性：
 * 1. 仅支持 TypeReference 泛型（彻底解决 List<实体> 反序列化问题，无类型推断歧义）
 * 2. 可配置化（通过配置文件注入参数，避免硬编码）
 * 3. 完善的日志监控（关键节点打印日志，便于排查问题）
 * 4. 增强的异常容错（线程中断、锁续期、任务拒绝等场景优化）
 * 5. 性能优化（批量操作、锁粒度控制）
 * 6. 简化接口（仅保留 TypeReference 版本，避免重载歧义）
 */
@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 注入分布式布隆过滤器管理器
    @Autowired
    private DistributedBloomFilterManager bloomFilterManager;


    // ========== 可配置化参数（支持 application.yml 注入） ==========
    @Value("${cache.client.null-ttl:2}")
    private Long CACHE_NULL_TTL; // 空值缓存过期时间（分钟）

    @Value("${cache.client.lock-prefix:lock:}")
    private String LOCK_PREFIX; // 锁前缀

    @Value("${cache.client.lock-ttl:10}")
    private Long LOCK_TTL; // 锁过期时间（秒）

    @Value("${cache.client.thread-pool.core-size:5}")
    private Integer CORE_POOL_SIZE; // 核心线程数

    @Value("${cache.client.thread-pool.max-size:10}")
    private Integer MAX_POOL_SIZE; // 最大线程数

    @Value("${cache.client.thread-pool.queue-size:100}")
    private Integer QUEUE_SIZE; // 任务队列大小

    @Value("${cache.client.retry-count:5}")
    private Integer DEFAULT_RETRY_COUNT; // 默认重试次数

    @Value("${cache.client.retry-interval:50}")
    private Long RETRY_INTERVAL; // 重试间隔（毫秒）
    // ========== 聚合缓存配置 ==========
    @Value("${cache.client.dep-prefix:dep:}")
    private String DEP_PREFIX; // 依赖关系Set前缀（存储单表Key关联的聚合Key）

    // ========== 线程池（可配置 + 优雅关闭） ==========
    private ExecutorService CACHE_REBUILD_EXECUTOR;

    // 初始化线程池（基于配置参数）
    @Autowired
    public void initThreadPool() {
        CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_SIZE),
                new ThreadFactory() {
                    private final AtomicInteger threadNum = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setName("cache-rebuild-thread-" + threadNum.getAndIncrement());
                        thread.setDaemon(true); // 守护线程，避免阻塞应用关闭
                        return thread;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行，避免任务丢失
        );

        // JVM关闭时优雅关闭线程池
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("开始关闭缓存重建线程池...");
            CACHE_REBUILD_EXECUTOR.shutdown();
            try {
                if (!CACHE_REBUILD_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("线程池未正常关闭，强制终止剩余任务");
                    CACHE_REBUILD_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                CACHE_REBUILD_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("缓存重建线程池已关闭");
        }));
    }

    @PostConstruct
    public void initAggCacheBloomFilter() {
        bloomFilterManager.initBloomFilter(AGG_CACHE_ORDER_BIZ_TYPE);
        log.info("聚合缓存布隆过滤器初始化完成 | BizType:{}", AGG_CACHE_ORDER_BIZ_TYPE);
    }

    // ========== 基础方法（仅 TypeReference 支持） ==========

    /**
     * 设置缓存（通用版，仅支持 TypeReference 反序列化）
     */
    public <T> void set(String key, T value, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key)) {
            log.warn("缓存Key为空，跳过设置");
            return;
        }
        if (value == null) {
            log.warn("缓存Value为空，Key:{}", key);
            value = (T) ""; // 空值统一存空字符串
        }
        try {
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, timeUnit);
            log.debug("缓存设置成功，Key:{}, 过期时间:{} {}", key, expireTime, timeUnit);
        } catch (Exception e) {
            log.error("缓存设置失败，Key:{}", key, e);
            throw new RuntimeException("缓存设置失败", e);
        }
    }

    /**
     * 设置逻辑过期缓存（通用版，仅 TypeReference）
     */
    public <T> void setWithLogicalExpire(String key, T value, Long expireTime, TimeUnit timeUnit) {
        if (StrUtil.isBlank(key)) {
            log.warn("缓存Key为空，跳过设置逻辑过期缓存");
            return;
        }
        if (value == null) {
            log.warn("缓存Value为空，Key:{}", key);
            return;
        }
        try {
            RedisData<T> redisData = new RedisData<>();
            redisData.setData(value);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
            log.debug("逻辑过期缓存设置成功，Key:{}, 逻辑过期时间:{}", key, redisData.getExpireTime());
        } catch (Exception e) {
            log.error("逻辑过期缓存设置失败，Key:{}", key, e);
            throw new RuntimeException("逻辑过期缓存设置失败", e);
        }
    }

    // ========== 缓存穿透（PassThrough）- 仅 TypeReference 版 ==========

    /**
     * 缓存穿透（简化版：keyPrefix + id 生成Key，仅 TypeReference）
     */
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
     * 缓存穿透（核心版：自定义Key生成器，仅 TypeReference）
     */
    public <D, DTO> D queryWithPassThrough(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        validateParams(keyGenerator, dto, dbFallback);

        // 2. 生成Key
        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        // 3. 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            try {
                D data = JSONUtil.toBean(json, typeRef.getType(), false);
                log.debug("缓存穿透策略-命中缓存，Key:{}", key);
                return data;
            } catch (Exception e) {
                log.error("缓存反序列化失败，删除损坏缓存，Key:{}", key, e);
                stringRedisTemplate.delete(key);
            }
        }

        // 4. 缓存为空字符串（标记数据库无数据）
        if (json != null) {
            log.debug("缓存穿透策略-命中空值缓存，Key:{}", key);
            return null;
        }

        // 5. 缓存未命中，查询数据库
        log.debug("缓存穿透策略-缓存未命中，查询数据库，Key:{}", key);
        D data = dbFallback.apply(dto);

        // 6. 数据库无数据，缓存空值
        if (data == null) {
            set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("缓存穿透策略-数据库无数据，缓存空值，Key:{}", key);
            return null;
        }

        // 7. 数据库有数据，写入缓存
        set(key, data, time, timeUnit);
        return data;
    }

    /**
     * 批量缓存穿透（仅 TypeReference 版）
     */
    public <D, DTO> Map<DTO, D> batchQueryWithPassThrough(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        if (CollectionUtil.isEmpty(dtos)) {
            throw new IllegalArgumentException("批量查询的DTO列表不能为空");
        }
        validateBatchParams(keyGenerator, batchDbFallback);

        // 2. 构建DTO-Key映射
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

        // 3. 批量查询缓存
        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<String, D> cacheMap = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String json = jsonList.get(i);
            if (StrUtil.isNotBlank(json)) {
                try {
                    cacheMap.put(key, JSONUtil.toBean(json, typeRef.getType(), false));
                } catch (Exception e) {
                    log.error("批量缓存反序列化失败，删除损坏缓存，Key:{}", key, e);
                    stringRedisTemplate.delete(key);
                }
            } else if (json != null) {
                cacheMap.put(key, null); // 空值标记
            }
        }

        // 4. 筛选未命中的DTO
        List<DTO> missDtos = dtos.stream()
                .filter(dto -> !cacheMap.containsKey(dtoKeyMap.get(dto)))
                .collect(Collectors.toList());
        if (CollectionUtil.isEmpty(missDtos)) {
            log.debug("批量缓存穿透策略-全部命中缓存，命中数:{}", dtos.size());
            return buildResultMap(dtos, dtoKeyMap, cacheMap);
        }

        // 5. 批量查询数据库
        log.debug("批量缓存穿透策略-缓存未命中数:{}，查询数据库", missDtos.size());
        Map<DTO, D> dbMap = batchDbFallback.apply(missDtos);

        // 6. 批量写入缓存
        for (DTO dto : missDtos) {
            String key = dtoKeyMap.get(dto);
            D data = dbMap.get(dto);
            if (data == null) {
                set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                cacheMap.put(key, null);
            } else {
                set(key, data, time, timeUnit);
                cacheMap.put(key, data);
            }
        }

        // 7. 组装结果
        return buildResultMap(dtos, dtoKeyMap, cacheMap);
    }

    // ========== 互斥锁（Mutex）- 仅 TypeReference 版 ==========

    /**
     * 互斥锁（简化版：keyPrefix + id，默认重试次数，仅 TypeReference）
     */
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
     * 互斥锁（核心版：自定义Key生成器，指定重试次数，仅 TypeReference）
     */
    public <D, DTO> D queryWithMutex(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    ) {
        // 1. 入参校验
        validateParams(keyGenerator, dto, dbFallback);

        // 2. 生成Key
        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        // 3. 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            try {
                D data = JSONUtil.toBean(json, typeRef.getType(), false);
                log.debug("互斥锁策略-命中缓存，Key:{}", key);
                return data;
            } catch (Exception e) {
                log.error("缓存反序列化失败，删除损坏缓存，Key:{}", key, e);
                stringRedisTemplate.delete(key);
            }
        }

        // 4. 缓存为空字符串
        if (json != null) {
            log.debug("互斥锁策略-命中空值缓存，Key:{}", key);
            return null;
        }

        // 5. 缓存未命中，加锁重建
        String lockKey = LOCK_PREFIX + key;
        String lockValue = UUID.randomUUID().toString();
        AtomicReference<Future<?>> renewalFutureRef = new AtomicReference<>();
        D data = null;

        try {
            // 5.1 获取锁（重试机制）
            boolean isLock = false;
            while (retryCount >= 0 && !isLock) {
                isLock = tryLock(lockKey, lockValue);
                if (!isLock) {
                    retryCount--;
                    if (retryCount < 0) {
                        log.error("获取互斥锁失败，重试次数用尽，Key:{}", lockKey);
                        throw new RuntimeException("获取锁失败，请稍后重试");
                    }
                    Thread.sleep(RETRY_INTERVAL);
                    log.debug("获取互斥锁失败，重试次数剩余:{}，Key:{}", retryCount, lockKey);
                }
            }

            // 5.2 启动锁续期
            renewalFutureRef.set(startLockRenewal(lockKey, lockValue));

            // 5.3 双重检查缓存
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                D doubleCheckData = JSONUtil.toBean(json, typeRef, false);
                log.debug("互斥锁策略-双重检查命中缓存，Key:{}", key);
                return doubleCheckData;
            }

            // 5.4 查询数据库
            log.debug("互斥锁策略-查询数据库，Key:{}", key);
            data = dbFallback.apply(dto);

            // 5.5 处理数据库结果
            if (data == null) {
                set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                log.debug("互斥锁策略-数据库无数据，缓存空值，Key:{}", key);
                return null;
            }

            // 5.6 写入缓存
            set(key, data, time, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取互斥锁时线程中断，Key:{}", lockKey, e);
            throw new RuntimeException("线程中断", e);
        } finally {
            // 6. 释放锁 + 取消续期
            unlock(lockKey, lockValue);
            Future<?> renewalFuture = renewalFutureRef.get();
            if (renewalFuture != null && !renewalFuture.isCancelled()) {
                renewalFuture.cancel(true);
            }
        }

        return data;
    }

    /**
     * 批量互斥锁（仅 TypeReference 版）
     */
    public <D, DTO> Map<DTO, D> batchQueryWithMutex(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    ) {
        // 1. 入参校验
        if (CollectionUtil.isEmpty(dtos)) {
            throw new IllegalArgumentException("批量查询的DTO列表不能为空");
        }
        validateBatchParams(keyGenerator, batchDbFallback);

        // 2. 构建DTO-Key映射
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

        // 3. 批量查询缓存
        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<String, D> cacheMap = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String json = jsonList.get(i);
            if (StrUtil.isNotBlank(json)) {
                try {
                    cacheMap.put(key, JSONUtil.toBean(json, typeRef.getType(), false));
                } catch (Exception e) {
                    log.error("批量缓存反序列化失败，删除损坏缓存，Key:{}", key, e);
                    stringRedisTemplate.delete(key);
                }
            } else if (json != null) {
                cacheMap.put(key, null);
            }
        }

        // 4. 筛选未命中的DTO
        List<DTO> missDtos = dtos.stream()
                .filter(dto -> !cacheMap.containsKey(dtoKeyMap.get(dto)))
                .collect(Collectors.toList());
        if (CollectionUtil.isEmpty(missDtos)) {
            log.debug("批量互斥锁策略-全部命中缓存，命中数:{}", dtos.size());
            return buildResultMap(dtos, dtoKeyMap, cacheMap);
        }

        // 5. 批量加锁
        Map<DTO, String> dtoLockKeyMap = new HashMap<>();
        Map<DTO, String> dtoLockValueMap = new HashMap<>();
        Map<DTO, Future<?>> renewalFutureMap = new HashMap<>();
        boolean isAllLockSuccess = false;

        try {
            // 5.1 重试获取锁
            int currentRetry = retryCount;
            while (currentRetry >= 0 && !isAllLockSuccess) {
                isAllLockSuccess = true;
                // 逐个获取锁
                for (DTO dto : missDtos) {
                    String lockKey = LOCK_PREFIX + dtoKeyMap.get(dto);
                    String lockValue = UUID.randomUUID().toString();
                    if (!tryLock(lockKey, lockValue)) {
                        isAllLockSuccess = false;
                        releaseBatchLock(missDtos, dtoLockKeyMap, dtoLockValueMap, renewalFutureMap);
                        currentRetry--;
                        if (currentRetry < 0) {
                            log.error("批量获取锁失败，重试次数用尽，未命中数:{}", missDtos.size());
                            throw new RuntimeException("批量获取锁失败，请稍后重试");
                        }
                        Thread.sleep(RETRY_INTERVAL);
                        break;
                    }
                    dtoLockKeyMap.put(dto, lockKey);
                    dtoLockValueMap.put(dto, lockValue);
                    renewalFutureMap.put(dto, startLockRenewal(lockKey, lockValue));
                }
            }

            // 5.2 双重检查缓存
            refreshCacheMap(missDtos, dtoKeyMap, cacheMap, typeRef);

            // 5.3 筛选最终未命中的DTO
            List<DTO> finalMissDtos = missDtos.stream()
                    .filter(dto -> !cacheMap.containsKey(dtoKeyMap.get(dto)))
                    .collect(Collectors.toList());

            // 5.4 批量查询数据库
            if (!CollectionUtil.isEmpty(finalMissDtos)) {
                log.debug("批量互斥锁策略-最终未命中数:{}，查询数据库", finalMissDtos.size());
                Map<DTO, D> dbMap = batchDbFallback.apply(finalMissDtos);

                // 5.5 批量写入缓存
                for (DTO dto : finalMissDtos) {
                    String key = dtoKeyMap.get(dto);
                    D data = dbMap.getOrDefault(dto, null);
                    if (data == null) {
                        set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        cacheMap.put(key, null);
                    } else {
                        set(key, data, time, timeUnit);
                        cacheMap.put(key, data);
                    }
                }
            }

            // 5.6 组装结果
            return buildResultMap(dtos, dtoKeyMap, cacheMap);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("批量获取锁时线程中断", e);
            throw new RuntimeException("线程中断", e);
        } finally {
            // 6. 释放所有锁 + 取消续期
            releaseBatchLock(missDtos, dtoLockKeyMap, dtoLockValueMap, renewalFutureMap);
        }
    }

    // ========== 逻辑过期（LogicalExpire）- 仅 TypeReference 版 ==========

    /**
     * 逻辑过期（简化版：keyPrefix + id，仅 TypeReference）
     */
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
     * 逻辑过期（核心版：自定义Key生成器，仅 TypeReference）
     */
    public <D, DTO> D queryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        validateParams(keyGenerator, dto, dbFallback);

        // 2. 生成Key
        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        // 3. 查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            log.debug("逻辑过期策略-缓存未命中，直接查询数据库，Key:{}", key);
            D dbData = dbFallback.apply(dto);
            if (dbData != null) {
                setWithLogicalExpire(key, dbData, time, timeUnit);
            }
            return dbData;
        }

        // 4. 反序列化RedisData（纯 TypeReference 版）
        TypeReference<RedisData<D>> redisDataRef = new TypeReference<RedisData<D>>() {};
        RedisData<D> redisData = JSONUtil.toBean(json, redisDataRef.getType(), false);
        if (redisData == null || redisData.getData() == null) {
            log.error("逻辑过期缓存反序列化失败，删除损坏缓存，Key:{}", key);
            stringRedisTemplate.delete(key);
            D dbData = dbFallback.apply(dto);
            if (dbData != null) {
                setWithLogicalExpire(key, dbData, time, timeUnit);
            }
            return dbData;
        }

        D data = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 缓存未过期，直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            log.debug("逻辑过期策略-缓存未过期，直接返回，Key:{}", key);
            return data;
        }

        // 6. 缓存过期，加锁异步重建
        String lockKey = LOCK_PREFIX + key;
        String lockValue = UUID.randomUUID().toString();
        AtomicReference<Future<?>> renewalFutureRef = new AtomicReference<>();
        boolean isLock = tryLock(lockKey, lockValue);

        if (isLock) {
            renewalFutureRef.set(startLockRenewal(lockKey, lockValue));
            // 6.1 二次校验缓存
            String newJson = stringRedisTemplate.opsForValue().get(key);
            TypeReference<RedisData<D>> newRedisDataRef = new TypeReference<RedisData<D>>() {};
            RedisData<D> newRedisData = JSONUtil.toBean(newJson, newRedisDataRef.getType(), false);
            if (newRedisData != null && newRedisData.getExpireTime().isAfter(LocalDateTime.now())) {
                unlock(lockKey, lockValue);
                renewalFutureRef.get().cancel(true);
                return newRedisData.getData();
            }

            // 6.2 异步重建缓存
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        D dbData = dbFallback.apply(dto);
                        if (dbData != null) {
                            setWithLogicalExpire(key, dbData, time, timeUnit);
                            log.debug("逻辑过期策略-异步重建缓存成功，Key:{}", key);
                        }
                    } catch (Exception e) {
                        log.error("逻辑过期策略-异步重建缓存失败，Key:{}", key, e);
                    } finally {
                        unlock(lockKey, lockValue);
                        Future<?> renewalFuture = renewalFutureRef.get();
                        if (renewalFuture != null && !renewalFuture.isCancelled()) {
                            renewalFuture.cancel(true);
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                // 线程池满，同步重建
                log.warn("缓存重建线程池满，同步重建缓存，Key:{}", key);
                D dbData = dbFallback.apply(dto);
                if (dbData != null) {
                    setWithLogicalExpire(key, dbData, time, timeUnit);
                }
                unlock(lockKey, lockValue);
                renewalFutureRef.get().cancel(true);
            }
        }

        // 7. 返回旧数据
        log.debug("逻辑过期策略-返回旧数据，异步重建缓存，Key:{}", key);
        return data;
    }

    /**
     * 批量逻辑过期（仅 TypeReference 版）
     */
    public <D, DTO> Map<DTO, D> batchQueryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        if (CollectionUtil.isEmpty(dtos)) {
            throw new IllegalArgumentException("批量查询的DTO列表不能为空");
        }
        validateBatchParams(keyGenerator, batchDbFallback);

        // 2. 构建映射关系
        Map<DTO, String> dtoKeyMap = new HashMap<>();
        List<String> keys = new ArrayList<>();
        Map<DTO, RedisData<D>> dtoRedisDataMap = new HashMap<>();
        Map<DTO, D> resultMap = new LinkedHashMap<>();
        List<DTO> missDtos = new ArrayList<>();
        List<DTO> expiredDtos = new ArrayList<>();

        // 3. 构建DTO-Key映射
        for (DTO dto : dtos) {
            String key = keyGenerator.apply(dto);
            if (StrUtil.isBlank(key)) {
                throw new IllegalArgumentException("DTO生成的缓存Key不能为空：" + dto);
            }
            dtoKeyMap.put(dto, key);
            keys.add(key);
        }

        // 4. 批量查询缓存
        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(keys);
        TypeReference<RedisData<D>> redisDataRef = new TypeReference<RedisData<D>>() {};

        for (int i = 0; i < dtos.size(); i++) {
            DTO dto = dtos.get(i);
            String key = keys.get(i);
            String json = jsonList.get(i);

            if (StrUtil.isBlank(json)) {
                missDtos.add(dto);
                continue;
            }

            // 反序列化RedisData（纯 TypeReference）
            RedisData<D> redisData = JSONUtil.toBean(json, redisDataRef.getType(), false);
            if (redisData == null || redisData.getData() == null) {
                missDtos.add(dto);
                stringRedisTemplate.delete(key);
                continue;
            }
            dtoRedisDataMap.put(dto, redisData);

            // 判断过期状态
            LocalDateTime expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                resultMap.put(dto, redisData.getData());
            } else {
                resultMap.put(dto, redisData.getData());
                expiredDtos.add(dto);
            }
        }

        // 5. 处理未命中的DTO（同步查库）
        if (!CollectionUtil.isEmpty(missDtos)) {
            log.debug("批量逻辑过期策略-未命中数:{}，同步查询数据库", missDtos.size());
            Map<DTO, D> missDbMap = batchDbFallback.apply(missDtos);
            for (DTO dto : missDtos) {
                D data = missDbMap.getOrDefault(dto, null);
                resultMap.put(dto, data);
                if (data != null) {
                    setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
                }
            }
        }

        // 6. 处理过期的DTO（异步重建）
        if (!CollectionUtil.isEmpty(expiredDtos)) {
            rebuildExpiredBatchCache(expiredDtos, dtoKeyMap, typeRef, batchDbFallback, time, timeUnit);
        }

        return resultMap;
    }


// ========== 聚合缓存（单Key关联多表Key）- 仅 TypeReference 版 ==========
    /**
     * 1.布隆过滤器：分页不适用，组合太多，容易引发维度爆炸
     * 聚合缓存查询（存储聚合结果 + 自动记录单表依赖关系）
     * @param aggKey 聚合缓存Key（如 rail:agg:order_full:123）
     * @param dependSingleKeys 该聚合依赖的所有单表Key（如 [rail:order:123, rail:order_details:456]）
     * @param typeRef 聚合数据类型（TypeReference，兼容泛型）
     * @param dbFallback DB查询回调（缓存未命中时执行）
     * @param dto 入参DTO（传递给dbFallback）
     * @param bizType 布隆过滤器业务类型（如"agg_cache_order"）
     * @param time 缓存过期时间
     * @param timeUnit 时间单位
     * @return 聚合数据
     */
    public <D, DTO> D queryAggCache(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            String bizType,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验（和原有逻辑一致）
        if (StrUtil.isBlank(aggKey)) {
            throw new IllegalArgumentException("聚合缓存Key不能为空");
        }
        if (StrUtil.isBlank(bizType)) {
            throw new IllegalArgumentException("布隆过滤器业务类型不能为空");
        }
        if (CollectionUtil.isEmpty(dependSingleKeys)) {
            log.warn("聚合缓存依赖的单表Key列表为空，AggKey:{}", aggKey);
        }
        validateParams((d) -> aggKey, dto, dbFallback);

        // 2. 布隆过滤器前置拦截（判断是否是“可能有数据的Key”）
        // 布隆返回false → 肯定无数据，直接返回null（替代原空值缓存逻辑）
        boolean mightExist = bloomFilterManager.mightContain(bizType, aggKey);
        if (!mightExist) {
            log.debug("聚合缓存策略-布隆过滤器拦截无效Key，直接返回null | AggKey:{}, BizType:{}", aggKey, bizType);
            return null;
        }
        // 3. 查询聚合缓存
        String json = stringRedisTemplate.opsForValue().get(aggKey);
        if (StrUtil.isNotBlank(json)) {
            try {
                D data = JSONUtil.toBean(json, typeRef.getType(), false);
                log.debug("聚合缓存策略-命中缓存，AggKey:{}", aggKey);
                return data;
            } catch (Exception e) {
                log.error("聚合缓存反序列化失败，删除损坏缓存，AggKey:{}", aggKey, e);
                stringRedisTemplate.delete(aggKey);
            }
        }

        // 4. 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        D data = dbFallback.apply(dto);

        // 5. 数据库无数据 → 直接返回null（不再缓存空值，靠布隆拦截后续请求）
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            log.debug("聚合缓存策略-数据库无数据，直接返回null | AggKey:{}", aggKey);
            return null;
        }

        // 6. 数据库有数据 → ①写入聚合缓存 ②记录依赖 ③将aggKey加入布隆
        // 6.1 写入聚合缓存
        set(aggKey, data, time, timeUnit);
        // 6.2 记录单表依赖关系
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                stringRedisTemplate.opsForSet().add(depSetKey, aggKey); // Set自动去重
                log.debug("聚合缓存策略-记录依赖关系，SingleKey:{}, AggKey:{}", singleKey, aggKey);
            }
        }
        // 6.3 核心：将“有数据的有效aggKey”加入布隆过滤器
        bloomFilterManager.add(bizType, aggKey);
        log.debug("聚合缓存策略-有效Key加入布隆过滤器 | AggKey:{}, BizType:{}", aggKey, bizType);

        return data;
    }

    /**
     * 聚合缓存查询（布隆过滤优化版：从AggCacheResult提取依赖单表Key）
     * @param aggKey 聚合缓存Key
     * @param typeRef 聚合数据类型
     * @param dbFallback DB查询回调（返回AggCacheResult，包含数据+依赖单表Key）
     * @param dto 入参DTO
     * @param bizType 布隆过滤器业务类型
     * @param time 缓存过期时间
     * @param timeUnit 时间单位
     * @return 聚合数据
     */
    public <D, DTO> D queryAggCache(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            String bizType,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        if (StrUtil.isBlank(aggKey)) {
            throw new IllegalArgumentException("聚合缓存Key不能为空");
        }
        if (StrUtil.isBlank(bizType)) {
            throw new IllegalArgumentException("布隆过滤器业务类型不能为空");
        }
        validateParams((d) -> aggKey, dto, dbFallback);

        // 2. 布隆过滤器前置拦截
        boolean mightExist = bloomFilterManager.mightContain(bizType, aggKey);
        if (!mightExist) {
            log.debug("聚合缓存策略-布隆过滤器拦截无效Key，直接返回null | AggKey:{}, BizType:{}", aggKey, bizType);
            return null;
        }

        // 3. 查询聚合缓存
        String json = stringRedisTemplate.opsForValue().get(aggKey);
        if (StrUtil.isNotBlank(json)) {
            try {
                D data = JSONUtil.toBean(json, typeRef.getType(), false);
                log.debug("聚合缓存策略-命中缓存，AggKey:{}", aggKey);
                return data;
            } catch (Exception e) {
                log.error("聚合缓存反序列化失败，删除损坏缓存，AggKey:{}", aggKey, e);
                stringRedisTemplate.delete(aggKey);
            }
        }

        // 4. 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        AggCacheResult<D> aggResult = dbFallback.apply(dto);
        D data = aggResult.getData();
        List<String> dependSingleKeys = aggResult.getDependSingleKeys();

        // 5. 数据库无数据 → 直接返回null（移除空值缓存）
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            log.debug("聚合缓存策略-数据库无数据，直接返回null | AggKey:{}", aggKey);
            return null;
        }

        // 6. 数据库有数据 → 写入缓存 + 记录依赖 + 加入布隆
        set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                stringRedisTemplate.opsForSet().add(depSetKey, aggKey);
                log.debug("聚合缓存策略-记录依赖关系，SingleKey:{}, AggKey:{}", singleKey, aggKey);
            }
        }
        // 有效Key加入布隆
        bloomFilterManager.add(bizType, aggKey);
        log.debug("聚合缓存策略-有效Key加入布隆过滤器 | AggKey:{}, BizType:{}", aggKey, bizType);

        return data;
    }

    /**
     * 2，缓存空值
     * 聚合缓存查询（存储聚合结果 + 自动记录单表依赖关系）
     * @param aggKey 聚合缓存Key（如 rail:agg:order_full:123）
     * @param dependSingleKeys 该聚合依赖的所有单表Key（如 [rail:order:123, rail:order_details:456]）
     * @param typeRef 聚合数据类型（TypeReference，兼容泛型）
     * @param dbFallback DB查询回调（缓存未命中时执行）
     * @param dto 入参DTO（传递给dbFallback）
     * @param time 缓存过期时间
     * @param timeUnit 时间单位
     * @return 聚合数据
     */
    public <D, DTO> D queryAggCache(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        if (StrUtil.isBlank(aggKey)) {
            throw new IllegalArgumentException("聚合缓存Key不能为空");
        }
        if (CollectionUtil.isEmpty(dependSingleKeys)) {
            log.warn("聚合缓存依赖的单表Key列表为空，AggKey:{}", aggKey);
        }
        validateParams((d) -> aggKey, dto, dbFallback);

        // 2. 查询聚合缓存
        String json = stringRedisTemplate.opsForValue().get(aggKey);
        if (StrUtil.isNotBlank(json)) {
            try {
                D data = JSONUtil.toBean(json, typeRef.getType(), false);
                log.debug("聚合缓存策略-命中缓存，AggKey:{}", aggKey);
                return data;
            } catch (Exception e) {
                log.error("聚合缓存反序列化失败，删除损坏缓存，AggKey:{}", aggKey, e);
                stringRedisTemplate.delete(aggKey);
            }
        }

        // 3. 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        D data = dbFallback.apply(dto);

        // 4. 数据库无数据 → 缓存空值（短TTL）+ 返回null
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            // 缓存空字符串（Redis中null会被视为key不存在，空字符串更易识别空值缓存）
            stringRedisTemplate.opsForValue().set(aggKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", CACHE_NULL_TTL, aggKey);
            return null;
        }

        // 5. 数据库有数据 → ①写入聚合缓存 ②记录依赖
        // 5.1 写入聚合缓存
        set(aggKey, data, time, timeUnit);
        // 5.2 记录单表依赖关系
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                stringRedisTemplate.opsForSet().add(depSetKey, aggKey); // Set自动去重
                log.debug("聚合缓存策略-记录依赖关系，SingleKey:{}, AggKey:{}", singleKey, aggKey);
            }
        }

        return data;
    }

    /**
     * 聚合缓存查询（纯空值缓存版：从AggCacheResult提取依赖单表Key，无布隆过滤器）
     * @param aggKey 聚合缓存Key
     * @param typeRef 聚合数据类型
     * @param dbFallback DB查询回调（返回AggCacheResult，包含数据+依赖单表Key）
     * @param dto 入参DTO
     * @param time 正常数据缓存过期时间
     * @param timeUnit 正常数据缓存时间单位
     * @return 聚合数据
     */
    public <D, DTO> D queryAggCache(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        if (StrUtil.isBlank(aggKey)) {
            throw new IllegalArgumentException("聚合缓存Key不能为空");
        }
        validateParams((d) -> aggKey, dto, dbFallback);

        // 2. 查询聚合缓存
        String json = stringRedisTemplate.opsForValue().get(aggKey);
        if (StrUtil.isNotBlank(json)) {
            try {
                D data = JSONUtil.toBean(json, typeRef.getType(), false);
                log.debug("聚合缓存策略-命中缓存，AggKey:{}", aggKey);
                return data;
            } catch (Exception e) {
                log.error("聚合缓存反序列化失败，删除损坏缓存，AggKey:{}", aggKey, e);
                stringRedisTemplate.delete(aggKey);
            }
        }

        // 3. 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        AggCacheResult<D> aggResult = dbFallback.apply(dto);
        D data = aggResult.getData();
        List<String> dependSingleKeys = aggResult.getDependSingleKeys();

        // 4. 数据库无数据 → 缓存空值（短TTL）+ 返回null
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            // 缓存空字符串（Redis中null会被视为key不存在，空字符串更易识别空值缓存）
            stringRedisTemplate.opsForValue().set(aggKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", CACHE_NULL_TTL, aggKey);
            return null;
        }

        // 5. 数据库有数据 → 写入缓存 + 记录依赖
        set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                stringRedisTemplate.opsForSet().add(depSetKey, aggKey);
                log.debug("聚合缓存策略-记录依赖关系，SingleKey:{}, AggKey:{}", singleKey, aggKey);
            }
        }

        return data;
    }

    /**
     * 批量聚合缓存查询（纯空值缓存版：Key由keyGenerator生成，无布隆过滤器）
     * @param keyGenerator 聚合缓存Key生成器（入参单个DTO，返回对应AggKey，参考queryWithMutex的参数风格）
     * @param dtos 入参DTO列表（KeyGenerator基于此生成AggKey列表，一一对应）
     * @param typeRef 单个聚合数据的类型（如TypeReference<SeatClassVO>）
     * @param dbFallback DB批量查询回调（入参为未命中的DTO列表，返回AggBatchResult，包含Key-数据映射+依赖单表Key）
     * @param time 正常数据缓存过期时间
     * @param timeUnit 正常数据缓存时间单位
     * @return 聚合数据列表（和传入的dtos顺序完全一致，未命中且查库无数据则为null）
     */
    public <D, DTO> List<D> batchQueryAggCache(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, AggBatchResult<D>> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        // 1. 入参校验
        if (keyGenerator == null) {
            throw new IllegalArgumentException("聚合缓存Key生成器不能为空");
        }
        if (CollectionUtil.isEmpty(dtos)) {
            throw new IllegalArgumentException("DTO列表不能为空");
        }
        if (dbFallback == null) {
            throw new IllegalArgumentException("数据库批量查询回调不能为空");
        }

        // 核心修改：通过keyGenerator生成aggKeys列表（和dtos一一对应）
        List<String> aggKeys = dtos.stream()
                .map(keyGenerator) // 每个DTO对应生成一个AggKey
                .collect(Collectors.toList());

        // 校验生成的aggKeys：非空 + 无空白Key + 和dtos长度一致
        if (CollectionUtil.isEmpty(aggKeys) || aggKeys.size() != dtos.size()) {
            throw new IllegalArgumentException("Key生成器生成的AggKey列表不能为空，且长度必须和DTO列表一致");
        }
        for (int i = 0; i < aggKeys.size(); i++) {
            String aggKey = aggKeys.get(i);
            if (StrUtil.isBlank(aggKey)) {
                throw new IllegalArgumentException(String.format("Key生成器为第%d个DTO生成的AggKey为空", i));
            }
        }

        // 2. 批量查询聚合缓存（Redis multiGet）
        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(aggKeys);
        Map<String, D> cachedDataMap = new LinkedHashMap<>(); // 保持插入顺序
        List<String> missAggKeys = new ArrayList<>(); // 缓存未命中的Key
        List<DTO> missDtos = new ArrayList<>(); // 缓存未命中对应的DTO
        Type dataType = typeRef.getType();

        for (int i = 0; i < aggKeys.size(); i++) {
            String aggKey = aggKeys.get(i);
            String json = jsonList != null && jsonList.size() > i ? jsonList.get(i) : null;

            if (StrUtil.isNotBlank(json)) {
                try {
                    D data = JSONUtil.toBean(json, dataType, false);
                    cachedDataMap.put(aggKey, data);
                    log.debug("批量聚合缓存策略-命中缓存，AggKey:{}", aggKey);
                } catch (Exception e) {
                    log.error("批量聚合缓存反序列化失败，删除损坏缓存，AggKey:{}", aggKey, e);
                    stringRedisTemplate.delete(aggKey);
                    missAggKeys.add(aggKey);
                    missDtos.add(dtos.get(i));
                }
            } else {
                missAggKeys.add(aggKey);
                missDtos.add(dtos.get(i));
                log.debug("批量聚合缓存策略-缓存未命中，待查库，AggKey:{}", aggKey);
            }
        }

        // 3. 无未命中Key，直接返回缓存结果
        if (CollectionUtil.isEmpty(missAggKeys)) {
            return aggKeys.stream().map(cachedDataMap::get).collect(Collectors.toList());
        }

        // 4. 缓存未命中，批量查询数据库
        log.debug("批量聚合缓存策略-{}个Key缓存未命中，批量查询数据库", missAggKeys.size());
        AggBatchResult<D> aggBatchResult = dbFallback.apply(missDtos);
        Map<String, D> dbDataMap = aggBatchResult.getDataMap() == null ? new HashMap<>() : aggBatchResult.getDataMap();
        List<String> dependSingleKeys = aggBatchResult.getDependSingleKeys() == null ? new ArrayList<>() : aggBatchResult.getDependSingleKeys();

        // 5. 处理查库结果：空值缓存 + 正常数据缓存
        List<String> nullAggKeys = new ArrayList<>();
        Map<String, D> normalDataMap = new HashMap<>();

        for (String missAggKey : missAggKeys) {
            D dbData = dbDataMap.get(missAggKey);
            if (dbData == null || (dbData instanceof PageResult && ((PageResult<?>) dbData).getTotal() == 0)) {
                nullAggKeys.add(missAggKey);
                log.debug("批量聚合缓存策略-数据库无数据，待缓存空值，AggKey:{}", missAggKey);
            } else {
                normalDataMap.put(missAggKey, dbData);
            }
        }

        // 5.1 批量缓存空值（短TTL）
        if (CollectionUtil.isNotEmpty(nullAggKeys)) {
            Map<String, String> nullValueMap = nullAggKeys.stream()
                    .collect(Collectors.toMap(key -> key, key -> ""));
            stringRedisTemplate.opsForValue().multiSet(nullValueMap);
            nullAggKeys.forEach(key -> stringRedisTemplate.expire(key, CACHE_NULL_TTL, TimeUnit.MINUTES));
            log.debug("批量聚合缓存策略-批量缓存空值，共{}个Key，TTL:{}分钟", nullAggKeys.size(), CACHE_NULL_TTL);
        }

        // 5.2 批量缓存正常数据 + 记录依赖关系
        if (CollectionUtil.isNotEmpty(normalDataMap)) {
            Map<String, String> normalValueMap = normalDataMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> JSONUtil.toJsonStr(entry.getValue())
                    ));
            stringRedisTemplate.opsForValue().multiSet(normalValueMap);
            normalDataMap.keySet().forEach(key -> stringRedisTemplate.expire(key, time, timeUnit));
            log.debug("批量聚合缓存策略-批量缓存正常数据，共{}个Key，TTL:{} {}", normalDataMap.size(), time, timeUnit);

            if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
                for (String singleKey : dependSingleKeys) {
                    String depSetKey = buildDepSetKey(singleKey);
                    stringRedisTemplate.opsForSet().add(depSetKey, normalDataMap.keySet().toArray(new String[0]));
                    log.debug("批量聚合缓存策略-批量记录依赖关系，SingleKey:{}, 关联AggKey数量:{}", singleKey, normalDataMap.size());
                }
            }
        }

        // 6. 合并结果并返回（和dtos顺序一致）
        Map<String, D> finalDataMap = new LinkedHashMap<>(cachedDataMap);
        finalDataMap.putAll(normalDataMap);
        nullAggKeys.forEach(key -> finalDataMap.put(key, null));

        return aggKeys.stream().map(finalDataMap::get).collect(Collectors.toList());
    }


    /**
     * 自动清理聚合缓存（供切面调用）
     * 逻辑：删除单表缓存 → 读取dep Set删除聚合缓存 → 清空dep Set
     * @param singleKey 单表Key（如 rail:order:123）
     */
    public void autoClearAggCache(String singleKey) {
        if (StrUtil.isBlank(singleKey)) {
            log.warn("清理聚合缓存失败：单表Key为空");
            return;
        }

        try {
            // 步骤1：删除单表自身缓存
            stringRedisTemplate.delete(singleKey);
            log.debug("清理聚合缓存-删除单表缓存，SingleKey:{}", singleKey);

            // 步骤2：读取依赖Set，获取关联的聚合Key
            String depSetKey = buildDepSetKey(singleKey);
            Set<String> aggKeys = stringRedisTemplate.opsForSet().members(depSetKey);

            // 步骤3：批量删除聚合缓存 + 清空依赖Set
            if (aggKeys != null && CollectionUtil.isNotEmpty(aggKeys)) {
                stringRedisTemplate.delete(aggKeys);
                log.debug("清理聚合缓存-批量删除聚合Key，数量:{}, SingleKey:{}", aggKeys.size(), singleKey);
                stringRedisTemplate.delete(depSetKey); // 清空dep Set，减少空间占用
                log.debug("清理聚合缓存-清空依赖Set，DepSetKey:{}", depSetKey);
            } else {
                log.debug("清理聚合缓存-无关联聚合Key，SingleKey:{}", singleKey);
            }
        } catch (Exception e) {
            log.error("清理聚合缓存失败，SingleKey:{}", singleKey, e);
            throw new RuntimeException("清理聚合缓存失败", e);
        }
    }

    /**
     * 构建依赖Set的Key（辅助方法）
     * @param singleKey 单表Key
     * @return 如 rail:dep:rail:order:123
     */
    private String buildDepSetKey(String singleKey) {
        return DEP_PREFIX + singleKey;
    }
    // ========== 辅助方法（纯 TypeReference 适配） ==========

    /**
     * 单条查询入参校验
     */
    private <DTO, D> void validateParams(Function<DTO, String> keyGenerator, DTO dto, Function<DTO, D> dbFallback) {
        if (keyGenerator == null) {
            throw new IllegalArgumentException("Key生成器不能为空");
        }
        if (dto == null) {
            throw new IllegalArgumentException("DTO参数不能为空");
        }
        if (dbFallback == null) {
            throw new IllegalArgumentException("数据库查询回调不能为空");
        }
    }

    /**
     * 批量查询入参校验
     */
    private <DTO, D> void validateBatchParams(Function<DTO, String> keyGenerator, Function<List<DTO>, Map<DTO, D>> batchDbFallback) {
        if (keyGenerator == null) {
            throw new IllegalArgumentException("Key生成器不能为空");
        }
        if (batchDbFallback == null) {
            throw new IllegalArgumentException("数据库批量查询回调不能为空");
        }
    }

    /**
     * 构建结果Map（保留入参顺序）
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
     * 刷新缓存Map（纯 TypeReference 版）
     */
    private <D, DTO> void refreshCacheMap(
            List<DTO> missDtos,
            Map<DTO, String> dtoKeyMap,
            Map<String, D> cacheMap,
            TypeReference<D> typeRef
    ) {
        List<String> checkKeys = missDtos.stream()
                .map(dto -> dtoKeyMap.get(dto))
                .collect(Collectors.toList());
        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(checkKeys);

        for (int i = 0; i < missDtos.size(); i++) {
            DTO dto = missDtos.get(i);
            String key = checkKeys.get(i);
            String json = jsonList.get(i);

            if (StrUtil.isNotBlank(json)) {
                try {
                    cacheMap.put(key, JSONUtil.toBean(json, typeRef.getType(), false));
                } catch (Exception e) {
                    log.error("双重检查缓存反序列化失败，删除损坏缓存，Key:{}", key, e);
                    stringRedisTemplate.delete(key);
                }
            } else if (json != null) {
                cacheMap.put(key, null);
            }
        }
    }

    /**
     * 批量释放锁 + 取消续期
     */
    private <DTO> void releaseBatchLock(
            List<DTO> dtos,
            Map<DTO, String> dtoLockKeyMap,
            Map<DTO, String> dtoLockValueMap,
            Map<DTO, Future<?>> renewalFutureMap
    ) {
        for (DTO dto : dtos) {
            // 取消续期
            Future<?> future = renewalFutureMap.get(dto);
            if (future != null && !future.isCancelled()) {
                future.cancel(true);
            }
            // 释放锁
            String lockKey = dtoLockKeyMap.get(dto);
            String lockValue = dtoLockValueMap.get(dto);
            if (StrUtil.isNotBlank(lockKey) && StrUtil.isNotBlank(lockValue)) {
                unlock(lockKey, lockValue);
            }
        }
    }

    /**
     * 批量重建过期缓存（纯 TypeReference 版）
     */
    private <D, DTO> void rebuildExpiredBatchCache(
            List<DTO> expiredDtos,
            Map<DTO, String> dtoKeyMap,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        // 构建锁映射
        Map<DTO, String> dtoLockKeyMap = new HashMap<>();
        Map<DTO, String> dtoLockValueMap = new HashMap<>();
        Map<DTO, Future<?>> renewalFutureMap = new HashMap<>();

        // 批量获取锁
        for (DTO dto : expiredDtos) {
            String key = dtoKeyMap.get(dto);
            String lockKey = LOCK_PREFIX + key;
            String lockValue = UUID.randomUUID().toString();
            if (tryLock(lockKey, lockValue)) {
                dtoLockKeyMap.put(dto, lockKey);
                dtoLockValueMap.put(dto, lockValue);
                renewalFutureMap.put(dto, startLockRenewal(lockKey, lockValue));
            }
        }

        // 筛选成功获取锁的DTO
        List<DTO> lockSuccessDtos = expiredDtos.stream()
                .filter(dto -> renewalFutureMap.containsKey(dto))
                .collect(Collectors.toList());
        if (CollectionUtil.isEmpty(lockSuccessDtos)) {
            return;
        }

        // 异步重建
        try {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 双重检查缓存
                    List<DTO> finalRebuildDtos = new ArrayList<>();
                    List<String> checkKeys = lockSuccessDtos.stream()
                            .map(dto -> dtoKeyMap.get(dto))
                            .collect(Collectors.toList());
                    List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(checkKeys);
                    TypeReference<RedisData<D>> redisDataRef = new TypeReference<RedisData<D>>() {};

                    for (int i = 0; i < lockSuccessDtos.size(); i++) {
                        DTO dto = lockSuccessDtos.get(i);
                        String json = jsonList.get(i);
                        RedisData<D> redisData = JSONUtil.toBean(json, redisDataRef.getType(), false);
                        if (redisData == null || redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                            finalRebuildDtos.add(dto);
                        }
                    }

                    // 批量查询数据库
                    if (!CollectionUtil.isEmpty(finalRebuildDtos)) {
                        Map<DTO, D> dbMap = batchDbFallback.apply(finalRebuildDtos);
                        // 批量写入缓存
                        for (DTO dto : finalRebuildDtos) {
                            D data = dbMap.getOrDefault(dto, null);
                            if (data != null) {
                                setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
                            }
                        }
                        log.debug("批量逻辑过期策略-异步重建缓存成功，重建数:{}", finalRebuildDtos.size());
                    }
                } catch (Exception e) {
                    log.error("批量逻辑过期策略-异步重建缓存失败", e);
                } finally {
                    // 释放锁 + 取消续期
                    releaseBatchLock(lockSuccessDtos, dtoLockKeyMap, dtoLockValueMap, renewalFutureMap);
                }
            });
        } catch (RejectedExecutionException e) {
            // 线程池满，同步重建
            log.warn("缓存重建线程池满，同步重建批量过期缓存，重建数:{}", lockSuccessDtos.size());
            for (DTO dto : lockSuccessDtos) {
                try {
                    D data = batchDbFallback.apply(Collections.singletonList(dto)).get(dto);
                    if (data != null) {
                        setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
                    }
                } finally {
                    releaseBatchLock(Collections.singletonList(dto), dtoLockKeyMap, dtoLockValueMap, renewalFutureMap);
                }
            }
        }
    }

    /**
     * 获取分布式锁（带日志）
     */
    private boolean tryLock(String key, String value) {
        try {
            Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(
                    key, value, Duration.ofSeconds(LOCK_TTL)
            );
            boolean result = BooleanUtil.isTrue(isLock);
            if (result) {
                log.debug("获取分布式锁成功，LockKey:{}", key);
            } else {
                log.debug("获取分布式锁失败，LockKey:{}", key);
            }
            return result;
        } catch (Exception e) {
            log.error("获取分布式锁异常，LockKey:{}", key, e);
            return false;
        }
    }

    /**
     * 释放分布式锁（Lua脚本 + 日志）
     */
    private void unlock(String key, String value) {
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else " +
                "return 0 " +
                "end";
        try {
            Long result = stringRedisTemplate.execute(
                    new DefaultRedisScript<>(luaScript, Long.class),
                    Collections.singletonList(key),
                    value
            );
            if (result != null && result > 0) {
                log.debug("释放分布式锁成功，LockKey:{}", key);
            } else {
                log.debug("释放分布式锁失败（锁已过期/归属其他线程），LockKey:{}", key);
            }
        } catch (Exception e) {
            log.error("释放分布式锁异常，LockKey:{}", key, e);
        }
    }

    /**
     * 锁续期（优化日志 + 中断处理）
     */
    private Future<?> startLockRenewal(String lockKey, String lockValue) {
        Runnable renewalTask = () -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(LOCK_TTL * 1000L / 3); // 每1/3锁TTL续期一次

                    Boolean success = stringRedisTemplate.opsForValue().setIfPresent(
                            lockKey, lockValue, Duration.ofSeconds(LOCK_TTL)
                    );
                    if (BooleanUtil.isFalse(success)) {
                        log.debug("锁续期失败，LockKey:{}", lockKey);
                        break;
                    }
                    log.debug("锁续期成功，LockKey:{}", lockKey);
                }
            } catch (InterruptedException e) {
                log.debug("锁续期线程被中断，LockKey:{}", lockKey);
                Thread.currentThread().interrupt();
            }
        };
        return CACHE_REBUILD_EXECUTOR.submit(renewalTask);
    }
}