package org.rail.common.redis.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.result.PageResult;
import org.rail.common.redis.bloomfilter.DistributedBloomFilterManager;
import org.rail.common.redis.core.RedisAggCache;
import org.rail.common.redis.core.RedisCache;
import org.rail.common.redis.exception.CacheException;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rail.common.redis.constant.RedisConstants.CACHE_NULL_TTL;
import static org.rail.common.redis.constant.RedisConstants.DEP_PREFIX;

/**
 * 聚合缓存
 */
@Slf4j
public class RedissonAggCache implements RedisAggCache {

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedisCache redisCache;
    @Autowired
    private DistributedBloomFilterManager bloomFilterManager;

    // ========================== 聚合缓存（单Key关联多表Key） =========================
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
    @Override
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
        redisCache.set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                redisCache.addSetMember(depSetKey, aggKey);
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
    @Override
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
        redisCache.set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                redisCache.addSetMember(depSetKey, aggKey);
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
    @Override
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
            redisCache.set(aggKey, (D) "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", CACHE_NULL_TTL, aggKey);
            return null;
        }

        // 数据库有数据 → ①写入聚合缓存 ②记录依赖
        redisCache.set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                redisCache.addSetMember(depSetKey, aggKey);
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
    @Override
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
            redisCache.set(aggKey, (D) "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", CACHE_NULL_TTL, aggKey);
            return null;
        }

        // 5. 数据库有数据 → 写入缓存 + 记录依赖
        redisCache.set(aggKey, data, time, timeUnit);
        if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
            for (String singleKey : dependSingleKeys) {
                String depSetKey = buildDepSetKey(singleKey);
                redisCache.addSetMember(depSetKey, aggKey);
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
    @Override
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
        Map<String, D> cachedDataMap = redisCache.batchGet(aggKeys);

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
            redisCache.batchSet(nullValueMap, CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("批量聚合缓存策略-批量缓存空值，共{}个Key，TTL:{}分钟", nullAggKeys.size(), CACHE_NULL_TTL);
        }

        // 批量缓存正常数据 + 记录依赖关系
        if (CollectionUtil.isNotEmpty(normalDataMap)) {
            Map<String, String> normalValueMap = normalDataMap.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> JSONUtil.toJsonStr(entry.getValue())
                    ));
            redisCache.batchSet(normalValueMap, time, timeUnit);
            log.debug("批量聚合缓存策略-批量缓存正常数据，共{}个Key，TTL:{} {}", normalDataMap.size(), time, timeUnit);

            if (CollectionUtil.isNotEmpty(dependSingleKeys)) {
                for (String singleKey : dependSingleKeys) {
                    String depSetKey = buildDepSetKey(singleKey);
                    redisCache.addSetMember(depSetKey, normalDataMap.keySet().toArray(new String[0]));
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
    @Override
    public void autoClearAggCache(String singleKey) {
        if (StrUtil.isBlank(singleKey)) {
            throw new CacheException("清理聚合缓存失败：单表Key为空");
        }

        try {
            // 步骤1：删除单表自身缓存
            redisCache.delete(singleKey);
            log.debug("清理聚合缓存-删除单表缓存，SingleKey:{}", singleKey);

            // 步骤2：读取依赖Set，获取关联的聚合Key
            String depSetKey = buildDepSetKey(singleKey);
            Set<String> aggKeys = redisCache.getSetMembers(depSetKey);

            // 步骤3：批量删除聚合缓存 + 清空依赖Set
            if (aggKeys != null && CollectionUtil.isNotEmpty(aggKeys)) {
                redisCache.batchDelete(aggKeys);
                log.debug("清理聚合缓存-批量删除聚合Key，数量:{}, SingleKey:{}", aggKeys.size(), singleKey);
                redisCache.delete(depSetKey); // 清空dep Set，减少空间占用
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
}
