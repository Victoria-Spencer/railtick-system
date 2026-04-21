package org.rail.common.redis.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.result.PageResult;
import org.rail.common.redis.core.RedisAggCache;
import org.rail.common.redis.core.RedisCache;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.rail.common.redis.constant.RedisConstants.REDIS_CACHE_NULL_TTL;
import static org.rail.common.redis.constant.RedisConstants.REDIS_DEP_PREFIX;

/**
 * 聚合缓存
 */
@Slf4j
@Component
public class RedissonAggCache implements RedisAggCache {

    @Autowired
    private RedisCache redisCache;
    // ========================== 通用极简校验方法 ================================
    private static void validateRequired(Object param, String paramName) {
        if (param == null) {
            throw new IllegalArgumentException(StrUtil.format("参数【{}】不能为空", paramName));
        }
    }

    private static void validateKey(String key) {
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("缓存Key不能为空");
        }
    }

    private static <T> void validateCollectionNotEmpty(Collection<T> coll, String paramName) {
        validateRequired(coll, paramName);
        if (coll.isEmpty()) {
            throw new IllegalArgumentException(StrUtil.format("集合参数【{}】不能为空集合", paramName));
        }
    }

    private static void validateTimeParams(Long time, TimeUnit timeUnit) {
        validateRequired(time, "缓存过期时间");
        validateRequired(timeUnit, "时间单位");
        if (time <= 0) {
            throw new IllegalArgumentException("缓存过期时间必须大于0");
        }
    }

    // ============================== 组合封装校验 =======================================
    /**
     * 单条聚合缓存 统一校验
     */
    private static void validateAggCacheSingle(String aggKey, TypeReference<?> typeRef, Object dbFallback, Object dto, Long time, TimeUnit timeUnit) {
        validateKey(aggKey);
        validateRequired(typeRef, "typeRef");
        validateRequired(dbFallback, "dbFallback");
        validateRequired(dto, "dto");
        validateTimeParams(time, timeUnit);
    }

    /**
     * 批量聚合缓存 统一校验
     */
    private static void validateAggCacheBatch(Function<?, ?> keyGenerator, List<?> dtos, TypeReference<?> typeRef, Object dbFallback, Long time, TimeUnit timeUnit) {
        validateRequired(keyGenerator, "keyGenerator");
        validateCollectionNotEmpty(dtos, "dtos");
        validateRequired(typeRef, "typeRef");
        validateRequired(dbFallback, "dbFallback");
        validateTimeParams(time, timeUnit);
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
        validateAggCacheSingle(aggKey, typeRef, dbFallback, dto, time, timeUnit);

        // 查询聚合缓存
        D data = redisCache.get(aggKey);

        if (data != null) return data;
        if (redisCache.exists(aggKey)) return null;

        // 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        data = dbFallback.apply(dto);

        // 数据库无数据 → 缓存空值（短TTL）+ 返回null
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            redisCache.set(aggKey, (D) "", REDIS_CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", REDIS_CACHE_NULL_TTL, aggKey);
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
    public <D, DTO> D queryAggCacheWithNullCache(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    ) {
        validateAggCacheSingle(aggKey, typeRef, dbFallback, dto, time, timeUnit);

        D data = redisCache.get(aggKey);

        if (data != null) return data;
        if (redisCache.exists(aggKey)) return null;

        // 缓存未命中，查询数据库
        log.debug("聚合缓存策略-缓存未命中，查询数据库，AggKey:{}", aggKey);
        AggCacheResult<D> aggResult = dbFallback.apply(dto);
        data = aggResult.getData();
        List<String> dependSingleKeys = aggResult.getDependSingleKeys();

        // 数据库无数据 → 缓存空值（短TTL）+ 返回null
        if (data == null || (data instanceof PageResult && ((PageResult<?>) data).getTotal() == 0)) {
            redisCache.set(aggKey, (D) "", REDIS_CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("聚合缓存策略-数据库无数据，缓存空值（TTL:{}分钟） | AggKey:{}", REDIS_CACHE_NULL_TTL, aggKey);
            return null;
        }

        // 数据库有数据 → 写入缓存 + 记录依赖
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
        validateAggCacheBatch(keyGenerator, dtos, typeRef, dbFallback, time, timeUnit);

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
            redisCache.batchSet(nullValueMap, REDIS_CACHE_NULL_TTL, TimeUnit.MINUTES);
            log.debug("批量聚合缓存策略-批量缓存空值，共{}个Key，TTL:{}分钟", nullAggKeys.size(), REDIS_CACHE_NULL_TTL);
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
            throw new IllegalArgumentException("清理聚合缓存失败：单表Key为空");
        }

        try {
            // 删除单表自身缓存
            redisCache.delete(singleKey);
            log.debug("清理聚合缓存-删除单表缓存，SingleKey:{}", singleKey);

            // 读取依赖Set，获取关联的聚合Key
            String depSetKey = buildDepSetKey(singleKey);
            Set<String> aggKeys = redisCache.getSetMembers(depSetKey);

            // 批量删除聚合缓存 + 清空依赖Set
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
        return REDIS_DEP_PREFIX + singleKey;
    }
}
