package org.rail.common.redis.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.redis.bloomfilter.DistributedBloomFilterManager;
import org.rail.common.redis.exception.CacheException;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;
import org.rail.common.core.result.PageResult;
import org.rail.common.redis.result.RedisData;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rail.common.redis.constant.RedisConstants.AGG_CACHE_ORDER_BIZ_TYPE;

/**
 * 缓存客户端工具类
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
    private RedissonClient redissonClient;

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

    // 初始化线程池
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

    // =============================== String类型缓存操作封装 ===================================

    /**
     * 设置缓存
     */
    public <T> void set(String key, T value) {
        if (StrUtil.isBlank(key)) return;
        try {
            RBucket<T> bucket = redissonClient.getBucket(key);
            bucket.set(value == null ? (T) "" : value);
        } catch (Exception e) {
            throw new CacheException("缓存设置失败", e);
        }
    }

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
            log.debug("批量缓存设置成功，共{}个key，过期时间：{} {}", finalMap.size(), expireTime, timeUnit);
        } catch (Exception e) {
            throw new CacheException("批量缓存设置失败", e);
        }
    }

    /**
     * 批量获取缓存
     * 底层：Redisson RBuckets 批量get，性能极高
     */
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
    public <T> void addSetMember(String key, T value) {
        if (StrUtil.isBlank(key) || value == null) {
            return;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.add(value);
            log.debug("Set缓存添加成员成功，key:{}, value:{}", key, value);
        } catch (Exception e) {
            throw new CacheException("Set缓存添加成员失败", e);
        }
    }

    /**
     * 向Set缓存批量添加成员（对应原stringRedisTemplate.opsForSet().add(数组)）
     * 【核心特性：追加，不覆盖，自动去重】
     */
    public <T> void addSetMembers(String key, Collection<T> values) {
        if (StrUtil.isBlank(key) || CollectionUtil.isEmpty(values)) {
            return;
        }
        try {
            RSet<T> set = redissonClient.getSet(key);
            set.addAll(values);
            log.debug("Set缓存批量添加成员成功，key:{}, 成员数:{}", key, values.size());
        } catch (Exception e) {
            throw new CacheException("Set缓存批量添加成员失败", e);
        }
    }

    /**
     * 获取Set缓存所有成员（对应原stringRedisTemplate.opsForSet().members）
     */
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
            log.debug("批量缓存删除成功，共{}个有效key", validKeys.size());
        } catch (Exception e) {
            throw new CacheException("批量缓存删除失败", e);
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

    // ========================== 缓存穿透 ================================

    /**
     * 缓存穿透（keyPrefix + id 生成Key）
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
     * 缓存穿透（自定义Key生成器）
     */
    public <D, DTO> D queryWithPassThrough(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        // 查询缓存
        RBucket<D> bucket = redissonClient.getBucket(key);
        D data = bucket.get();
        if (data != null) {
            log.debug("缓存命中 key:{}", key);
            return data;
        }
        if (bucket.isExists()) return null;

        // 缓存未命中，查询数据库
        data = dbFallback.apply(dto);

        // 数据库无数据，缓存空值
        if (data == null) {
            set(key, (D) "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 数据库有数据，写入缓存
        set(key, data, time, timeUnit);
        return data;
    }

    /**
     * 批量缓存穿透
     */
    public <D, DTO> Map<DTO, D> batchQueryWithPassThrough(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
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
        Map<String, D> cacheMap = batchGet(keys);

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
            batchSet(nullMap, CACHE_NULL_TTL, TimeUnit.MINUTES);
        }
        if (MapUtil.isNotEmpty(normalMap)) {
            batchSet(normalMap, time, timeUnit);
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
    public <D, DTO> D queryWithMutex(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    ) {
        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        RBucket<D> bucket = redissonClient.getBucket(key);
        D data = bucket.get();

        if (data != null) return data;
        if (bucket.isExists()) return null;

        // 缓存未命中，加锁重建
        String lockKey = LOCK_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(0, LOCK_TTL, TimeUnit.SECONDS);
            if (!locked) {
                Thread.sleep(RETRY_INTERVAL);
                return queryWithMutex(keyGenerator, dto, typeRef, dbFallback, time, timeUnit, retryCount - 1);
            }

            // 二次检查
            data = bucket.get();
            if (data != null) return data;
            if (bucket.isExists()) return null;

            D dbData = dbFallback.apply(dto);
            if (dbData == null) {
                set(key, (D) "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            set(key, dbData, time, timeUnit);
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
    public <D, DTO> Map<DTO, D> batchQueryWithMutex(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    ) {
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
        Map<String, D> cacheMap = batchGet(keys);
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
                    RLock lock = redissonClient.getLock(LOCK_PREFIX + dtoKeyMap.get(dto));
                    try {
                        // Redisson 原生 tryLock：等待时间、锁过期时间、单位
                        if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_TTL, TimeUnit.SECONDS)) {
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
            Map<String, D> doubleCheckMap = batchGet(missDtos.stream().map(dtoKeyMap::get).toList());
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
                batchSet(writeMap, time, timeUnit);
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
    public <D, DTO> D queryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        String key = keyGenerator.apply(dto);
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("生成的缓存Key不能为空");
        }

        RBucket<RedisData<D>> bucket = redissonClient.getBucket(key);
        RedisData<D> redisData = bucket.get();

        if (redisData == null || redisData.getData() == null) {
            D dbData = dbFallback.apply(dto);
            if (dbData != null) setWithLogicalExpire(key, dbData, time, timeUnit);
            return dbData;
        }

        D data = redisData.getData();
        // 缓存未过期，直接返回
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) return data;

        String lockKey = LOCK_PREFIX + key;
        RLock lock = redissonClient.getLock(lockKey);

        // 缓存过期，加锁，异步重建
        try {
            if(lock.tryLock(2, TimeUnit.SECONDS)) {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        D dbData = dbFallback.apply(dto);
                        if (dbData != null) {
                            setWithLogicalExpire(key, dbData, time, timeUnit);
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
    public <D, DTO> Map<DTO, D> batchQueryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
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

        Map<String, RedisData<D>> cacheMap  =  batchGet(keys);

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

        // 处理过期的DTO（异步重建）
        if (CollectionUtil.isNotEmpty(expiredDtos)) {
            rebuildExpiredBatchCache(expiredDtos, dtoKeyMap, batchDbFallback, time, timeUnit);
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
        // 布隆过滤器前置拦截
        boolean mightExist = bloomFilterManager.mightContain(bizType, aggKey);
        if (!mightExist) {
            return null;
        }

        RBucket<D> bucket = redissonClient.getBucket(aggKey);
        D data = bucket.get();

        // 缓存未命中，查询数据库
        if (data != null) return data;
        data = dbFallback.apply(dto);

        if (data == null || (data instanceof PageResult<?> pr && pr.getTotal() == 0)) return null;

        // 6. 数据库有数据 → ①写入聚合缓存 ②记录依赖 ③将aggKey加入布隆
        set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                addSetMember(depSetKey, aggKey);
            }
        }
        bloomFilterManager.add(bizType, aggKey);

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
        // 布隆过滤器前置拦截
        boolean mightExist = bloomFilterManager.mightContain(bizType, aggKey);
        if (!mightExist) {
            return null;
        }

        RBucket<D> bucket = redissonClient.getBucket(aggKey);
        D data = bucket.get();

        if (data != null) return data;

        // 缓存未命中，查询数据库
        AggCacheResult<D> aggResult = dbFallback.apply(dto);
        data = aggResult.getData();
        List<String> dependSingleKeys = aggResult.getDependSingleKeys();

        if (data == null || (data instanceof PageResult<?> pr && pr.getTotal() == 0)) return null;

        // 6. 数据库有数据 → 写入缓存 + 记录依赖 + 加入布隆
        set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                addSetMember(depSetKey, aggKey);
            }
        }
        bloomFilterManager.add(bizType, aggKey);

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
        // 查询聚合缓存
        RBucket<D> bucket = redissonClient.getBucket(aggKey);
        D data = bucket.get();

        if (data != null) return data;
        if (bucket.isExists()) return null;

        // 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        data = dbFallback.apply(dto);

        // 数据库无数据 → 缓存空值（短TTL）+ 返回null
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            set(aggKey, (D) "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", CACHE_NULL_TTL, aggKey);
            return null;
        }

        // 数据库有数据 → ①写入聚合缓存 ②记录依赖
        set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                addSetMember(depSetKey, aggKey);
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
        RBucket<D> bucket = redissonClient.getBucket(aggKey);
        D data = bucket.get();

        if (data != null) return data;
        if (bucket.isExists()) return null;

        // 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        AggCacheResult<D> aggResult = dbFallback.apply(dto);
        data = aggResult.getData();
        List<String> dependSingleKeys = aggResult.getDependSingleKeys();

        // 4. 数据库无数据 → 缓存空值（短TTL）+ 返回null
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            set(aggKey, (D) "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", CACHE_NULL_TTL, aggKey);
            return null;
        }

        // 5. 数据库有数据 → 写入缓存 + 记录依赖
        set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                addSetMember(depSetKey, aggKey);
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
        // 通过keyGenerator生成aggKeys列表（和dtos一一对应）
        List<String> aggKeys = dtos.stream()
                .map(keyGenerator)
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

        // 批量查询聚合缓存
        Map<String, D> cachedDataMap = batchGet(aggKeys);

        List<String> missAggKeys = new ArrayList<>(); // 缓存未命中的Key
        List<DTO> missDtos = new ArrayList<>(); // 缓存未命中对应的DTO

        for (int i = 0; i < aggKeys.size(); i++) {
            String aggKey = aggKeys.get(i);
            D data = cachedDataMap.get(aggKey);

            if (data != null) {
                cachedDataMap.put(aggKey, data);
                log.debug("批量聚合缓存策略-命中缓存，AggKey:{}", aggKey);
            } else {
                missAggKeys.add(aggKey);
                missDtos.add(dtos.get(i));
                log.debug("批量聚合缓存策略-缓存未命中，待查库，AggKey:{}", aggKey);
            }
        }

        // 全部命中，直接返回
        if (CollectionUtil.isEmpty(missAggKeys)) {
            return aggKeys.stream().map(cachedDataMap::get).collect(Collectors.toList());
        }

        // 缓存未命中，批量查询数据库
        log.debug("批量聚合缓存策略-{}个Key缓存未命中，批量查询数据库", missAggKeys.size());
        AggBatchResult<D> aggBatchResult = dbFallback.apply(missDtos);
        Map<String, D> dbDataMap = aggBatchResult.getDataMap() == null ? new HashMap<>() : aggBatchResult.getDataMap();
        List<String> dependSingleKeys = aggBatchResult.getDependSingleKeys() == null ? new ArrayList<>() : aggBatchResult.getDependSingleKeys();

        // 处理查库结果：空值缓存 + 正常数据缓存
        List<String> nullAggKeys = new ArrayList<>();
        Map<String, D> normalDataMap = new HashMap<>();

        for (String missgKey : missAggKeys) {
            D dbData = dbDataMap.get(missgKey);
            if (dbData == null || (dbData instanceof PageResult && ((PageResult<?>) dbData).getTotal() == 0)) {
                nullAggKeys.add(missgKey);
                log.debug("批量聚合缓存策略-数据库无数据，待缓存空值，AggKey:{}", missgKey);
            } else {
                normalDataMap.put(missgKey, dbData);
            }
        }

        // 批量缓存空值（短TTL）
        if (CollectionUtil.isNotEmpty(nullAggKeys)) {
            Map<String, String> nullValueMap = nullAggKeys.stream()
                    .collect(Collectors.toMap(key -> key, key -> ""));
            batchSet(nullValueMap, CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("批量聚合缓存策略-批量缓存空值，共{}个Key，TTL:{}分钟", nullAggKeys.size(), CACHE_NULL_TTL);
        }

        // 批量缓存正常数据 + 记录依赖关系
        if (CollectionUtil.isNotEmpty(normalDataMap)) {
            Map<String, String> normalValueMap = normalDataMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> JSONUtil.toJsonStr(entry.getValue())
                    ));
            batchSet(normalValueMap, time, timeUnit);
            log.debug("批量聚合缓存策略-批量缓存正常数据，共{}个Key，TTL:{} {}", normalDataMap.size(), time, timeUnit);

            if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
                for (String singleKey : dependSingleKeys) {
                    String depSetKey = buildDepSetKey(singleKey);
                    addSetMember(depSetKey, normalDataMap.keySet().toArray(new String[0]));
                    log.debug("批量聚合缓存策略-批量记录依赖关系，SingleKey:{}, 关联AggKey数量:{}", singleKey, normalDataMap.size());
                }
            }
        }

        // 合并结果并返回（和dtos顺序一致）
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
            throw new CacheException("清理聚合缓存失败：单表Key为空");
        }

        try {
            // 步骤1：删除单表自身缓存
            delete(singleKey);
            log.debug("清理聚合缓存-删除单表缓存，SingleKey:{}", singleKey);

            // 步骤2：读取依赖Set，获取关联的聚合Key
            String depSetKey = buildDepSetKey(singleKey);
            Set<String> aggKeys = getSetMembers(depSetKey);

            // 步骤3：批量删除聚合缓存 + 清空依赖Set
            if (aggKeys != null && CollectionUtil.isNotEmpty(aggKeys)) {
                batchDelete(aggKeys);
                log.debug("清理聚合缓存-批量删除聚合Key，数量:{}, SingleKey:{}", aggKeys.size(), singleKey);
                delete(depSetKey); // 清空dep Set，减少空间占用
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
    // ============================== 辅助方法 ================================

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
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 1. 二次检查缓存
                List<String> checkKeys = expiredDtos.stream()
                        .map(dtoKeyMap::get)
                        .toList();
                Map<String, RedisData<D>> doubleCheckMap = batchGet(checkKeys);

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
                        setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
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
                    setWithLogicalExpire(dtoKeyMap.get(dto), data, time, timeUnit);
                }
            }
        }
    }
}