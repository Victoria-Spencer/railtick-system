package org.rail.common.redis.util;

import cn.hutool.core.lang.TypeReference;
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
     * 仅当key不存在时设置缓存
     */
    @Override
    public <T> Boolean setIfAbsent(String key, T value) {
        return redisCache.setIfAbsent(key, value);
    }

    /**
     * 仅当key不存在时设置缓存，并设置过期时间
     */
    @Override
    public <T> Boolean setIfAbsent(String key, T value, Long expireTime, TimeUnit timeUnit) {
        return redisCache.setIfAbsent(key, value, expireTime, timeUnit);
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
     * 单条获取缓存，支持传入类型
     */
    @Override
    public <T> T get(String key, Class<T> type) {
       return redisCache.get(key, type);
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
    public <T> void addSetMemberWithExpire(String key, T value, Long expireTime, TimeUnit timeUnit) {
        redisCache.addSetMemberWithExpire(key, value, expireTime, timeUnit);
    }

    /**
     * 向Set缓存批量添加成员，并设置过期时间
     */
    @Override
    public <T> void addSetMembersWithExpire(String key, Collection<T> values, Long expireTime, TimeUnit timeUnit) {
        redisCache.addSetMembersWithExpire(key, values, expireTime, timeUnit);
    }

    /**
     * 获取Set缓存所有成员（对应原stringRedisTemplate.opsForSet().members）
     */
    @Override
    public <T> Set<T> getSetMembers(String key) {
        return redisCache.getSetMembers(key);
    }

    // =============================== Bitmap 操作实现 ===============================

    /**
     * 设置Bitmap指定偏移量的值
     */
    @Override
    public void setBit(String key, long offset, boolean value) {
        redisCache.setBit(key, offset, value);
    }

    /**
     * 获取Bitmap指定偏移量的值
     */
    @Override
    public boolean getBit(String key, long offset) {
       return redisCache.getBit(key, offset);
    }

    /**
     * 统计Bitmap中1的数量
     */
    @Override
    public long bitCount(String key) {
        return redisCache.bitCount(key);
    }

    /**
     * 批量设置Bitmap多个偏移量为指定值
     */
    @Override
    public void batchSetBits(String key, Collection<Long> offsets, boolean value) {
        redisCache.batchSetBits(key, offsets, value);
    }

    /**
     * 设置Bitmap指定区间 [startOffset, endOffset) 所有位为指定值
     * 注意：RBitSet的set/clear是左闭右开区间
     */
    @Override
    public void setRangeBits(String key, long startOffset, long endOffset, boolean value) {
        redisCache.setRangeBits(key, startOffset, endOffset, value);
    }

    /**
     * 清空整个Bitmap（删除key）
     */
    @Override
    public void clearBitmap(String key) {
        redisCache.clearBitmap(key);
    }

    /**
     * 判断Bitmap指定区间[startOffset, endOffset)（左闭右开）是否全为0
     * 统计区间内1的数量，数量为0则代表全0
     */
    @Override
    public boolean isRangeAllZero(String key, long startOffset, long endOffset) {
        return redisCache.isRangeAllZero(key, startOffset, endOffset);
    }

    // =============================== Hash 操作实现 =================================

    /**
     * Hash 存入单个字段
     */
    @Override
    public <T> void hPut(String key, String hashKey, T value) {
        redisCache.hPut(key, hashKey, value);
    }

    /**
     * Hash 存入单个字段（带独立过期时间）
     * 效果：仅当前hashKey到期删除，其他field正常保留
     */
    @Override
    public <T> void hPut(String key, String hashKey, T value, Long expireTime, TimeUnit timeUnit) {
        redisCache.hPut(key, hashKey, value, expireTime, timeUnit);
    }

    /**
     * Hash 批量存入字段
     */
    @Override
    public <T> void hPutAll(String key, Map<String, T> map) {
        redisCache.hPutAll(key, map);
    }

    /**
     * Hash 批量存入字段（带过期时间）
     */
    @Override
    public <T> void hPutAll(String key, Map<String, T> map, Long expireTime, TimeUnit timeUnit) {
        redisCache.hPutAll(key, map, expireTime, timeUnit);
    }

    /**
     * Hash批量存入字段 + 给【整个Hash】设置过期时间
     * 效果：到期后 → 整个Hash被删除，所有字段全部清空
     */
    @Override
    public <T> void hPutAllWholeExpire(String key, Map<String, T> map, Long expireTime, TimeUnit timeUnit) {
        redisCache.hPutAllWholeExpire(key, map, expireTime, timeUnit);
    }

    /**
     * Hash 获取单个字段
     */
    @Override
    public <T> T hGet(String key, String hashKey) {
        return redisCache.hGet(key, hashKey);
    }

    /**
     * Hash 批量获取多个字段
     */
    @Override
    public <T> List<T> hMultiGet(String key, Collection<String> hashKeys) {
        return redisCache.hMultiGet(key, hashKeys);
    }

    /**
     * Hash 获取所有字段和值
     */
    @Override
    public <T> Map<String, T> hEntries(String key) {
        return redisCache.hEntries(key);
    }

    /**
     * Hash 获取所有字段名
     */
    @Override
    public Set<String> hKeys(String key) {
        return redisCache.hKeys(key);
    }

    /**
     * Hash 获取所有字段值
     */
    @Override
    public <T> List<T> hValues(String key) {
        return redisCache.hValues(key);
    }

    /**
     * Hash 删除指定字段
     */
    @Override
    public Long hDelete(String key, String... hashKeys) {
        return redisCache.hDelete(key, hashKeys);
    }

    /**
     * 判断 Hash 中是否存在指定字段
     */
    @Override
    public Boolean hExists(String key, String hashKey) {
        return redisCache.hExists(key, hashKey);
    }

    /**
     * Hash 字段数值自增/自减
     */
    @Override
    public Long hIncr(String key, String hashKey, long delta) {
        return redisCache.hIncr(key, hashKey, delta);
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

    // ================================= Lua 脚本操作 ==================================
    @Override
    public <T> T executeLuaFile(String luaFilePath, List<Object> keys, Object... args) {
        return redisCache.executeLuaFile(luaFilePath, keys, args);
    }

    @Override
    public <T> T executeLuaScript(String luaScript, List<Object> keys, Object... args) {
        return redisCache.executeLuaScript(luaScript, keys, args);
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
     * 缓存空值
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