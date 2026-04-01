package org.rail.common.redis.util;

import cn.hutool.core.lang.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.redis.api.ICacheClient;
import org.rail.common.redis.core.RedisAggCache;
import org.rail.common.redis.core.RedisCache;
import org.rail.common.redis.core.RedisStrategyCache;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

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
public class CacheClient implements ICacheClient {

    @Autowired
    private RedisCache redisCache;
    @Autowired
    private RedisStrategyCache redisStrategyCache;
    @Autowired
    private RedisAggCache redisAggCache;

    // =============================== String类型缓存操作封装 ===================================

    /**
     * 设置缓存
     */
    @Override
    public <T> void set(String key, T value) {
        redisCache.set(key, value);
    }

    @Override
    public <T> void set(String key, T value, Long expireTime, TimeUnit timeUnit) {
        redisCache.set(key, value, expireTime, timeUnit);
    }

    /**
     * 设置逻辑过期缓存
     */
    @Override
    public <T> void setWithLogicalExpire(String key, T value, Long expireTime, TimeUnit timeUnit) {
        redisCache.setWithLogicalExpire(key, value, expireTime, timeUnit);
    }

    /**
     * 批量设置缓存（无过期时间）
     */
    @Override
    public <T> void batchSet(Map<String, T> keyValueMap) {
        redisCache.batchSet(keyValueMap);
    }

    /**
     * 批量设置缓存（带统一过期时间）
     */
    @Override
    public <T> void batchSet(Map<String, T> keyValueMap, Long expireTime, TimeUnit timeUnit) {
        redisCache.batchSet(keyValueMap, expireTime, timeUnit);
    }

    /**
     * 单条获取缓存（自动处理空值"" -> null）
     */
    @Override
    public <T> T get(String key) {
        return redisCache.get(key);
    }

    /**
     * 批量获取缓存
     */
    @Override
    public <T> Map<String, T> batchGet(Collection<String> keys) {
        return redisCache.batchGet(keys);
    }

    /**
     * 判断缓存是否存在
     */
    @Override
    public boolean exists(String key) {
        return redisCache.exists(key);
    }

    // =============================== Set类型缓存操作封装 ===================================
    /**
     * 向Set缓存添加单个成员（对应原stringRedisTemplate.opsForSet().add）
     */
    @Override
    public <T> void addSetMember(String key, T value) {
        redisCache.addSetMember(key, value);
    }

    /**
     * 向Set缓存批量添加成员
     */
    @Override
    public <T> void addSetMembers(String key, Collection<T> values) {
        redisCache.addSetMembers(key, values);
    }

    /**
     * 向Set缓存添加单个成员，并设置过期时间
     */
    @Override
    public <T> void addSetMemberWithExpire(String key, T value, long expireTime, TimeUnit timeUnit) {
        redisCache.addSetMemberWithExpire(key, value, expireTime, timeUnit);
    }

    /**
     * 向Set缓存批量添加成员，并设置过期时间
     */
    @Override
    public <T> void addSetMembersWithExpire(String key, Collection<T> values, long expireTime, TimeUnit timeUnit) {
        redisCache.addSetMembersWithExpire(key, values, expireTime, timeUnit);
    }

    /**
     * 获取Set缓存所有成员（对应原stringRedisTemplate.opsForSet().members）
     */
    @Override
    public <T> Set<T> getSetMembers(String key) {
        return redisCache.getSetMembers(key);
    }

    // ========================== 缓存删除封装（String、Set通用） =========================
    /**
     * 删除缓存
     */
    @Override
    public void delete(String key) {
        redisCache.delete(key);
    }

    /**
     * 批量删除缓存
     */
    @Override
    public void batchDelete(Collection<String> keys) {
        redisCache.batchDelete(keys);
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
        return redisStrategyCache.queryWithPassThrough(keyPrefix, id, typeRef, dbFallback, time, timeUnit);
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
        return redisStrategyCache.queryWithPassThrough(keyGenerator, dto, typeRef, dbFallback, time, timeUnit);
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
        return redisStrategyCache.batchQueryWithPassThrough(keyGenerator, dtos, typeRef, batchDbFallback, time, timeUnit);
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
        return redisStrategyCache.queryWithMutex(keyPrefix, id, typeRef, dbFallback, time, timeUnit);
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
        return redisStrategyCache.queryWithMutex(keyGenerator, dto, typeRef, dbFallback, time, timeUnit, retryCount);
    }

    /**
     * 批量互斥锁
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
        return redisStrategyCache.batchQueryWithMutex(keyGenerator, dtos, typeRef, batchDbFallback, time, timeUnit, retryCount);
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
        return redisStrategyCache.queryWithLogicalExpire(keyPrefix, id, typeRef, dbFallback, time, timeUnit);
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
        return redisStrategyCache.queryWithLogicalExpire(keyGenerator, dto, typeRef, dbFallback, time, timeUnit);
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
        return redisStrategyCache.batchQueryWithLogicalExpire(keyGenerator, dtos, typeRef, batchDbFallback, time, timeUnit);
    }


// ========================== 聚合缓存（单Key关联多表Key） =========================
    /**
     * 1.布隆过滤器：分页不适用，组合太多，容易引发维度爆炸
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
    @Override
    public <D, DTO> D queryAggCacheWithBloom(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    ) {
        return redisAggCache.queryAggCacheWithBloom(aggKey, dependSingleKeys, typeRef, dbFallback, dto, time, timeUnit);
    }

    /**
     * 聚合缓存查询（布隆过滤优化版：从AggCacheResult提取依赖单表Key）
     * @param aggKey 聚合缓存Key
     * @param typeRef 聚合数据类型
     * @param dbFallback DB查询回调（返回AggCacheResult，包含数据+依赖单表Key）
     * @param dto 入参DTO
     * @param time 缓存过期时间
     * @param timeUnit 时间单位
     * @return 聚合数据
     */
    @Override
    public <D, DTO> D queryAggCacheWithBloom(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    ) {
        return redisAggCache.queryAggCacheWithBloom(aggKey, typeRef, dbFallback, dto, time, timeUnit);
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
    @Override
    public <D, DTO> D queryAggCacheWithNullCache(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    ) {
        return redisAggCache.queryAggCacheWithNullCache(aggKey, dependSingleKeys, typeRef, dbFallback, dto, time, timeUnit);
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
    @Override
    public <D, DTO> D queryAggCacheWithNullCache(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    ) {
        return redisAggCache.queryAggCacheWithNullCache(aggKey, typeRef, dbFallback, dto, time, timeUnit);
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
    @Override
    public <D, DTO> List<D> batchQueryAggCache(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, AggBatchResult<D>> dbFallback,
            Long time,
            TimeUnit timeUnit
    ) {
        return redisAggCache.batchQueryAggCache(keyGenerator, dtos, typeRef, dbFallback, time, timeUnit);
    }


    /**
     * 自动清理聚合缓存（供切面调用）
     * 逻辑：删除单表缓存 → 读取dep Set删除聚合缓存 → 清空dep Set
     * @param singleKey 单表Key（如 rail:order:123）
     */
    @Override
    public void autoClearAggCache(String singleKey) {
        redisAggCache.autoClearAggCache(singleKey);
    }
}