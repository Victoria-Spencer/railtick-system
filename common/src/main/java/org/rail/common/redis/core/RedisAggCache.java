package org.rail.common.redis.core;

import cn.hutool.core.lang.TypeReference;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface RedisAggCache {
    <D, DTO> D queryAggCache(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            String bizType,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryAggCache(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            String bizType,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryAggCache(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryAggCache(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> List<D> batchQueryAggCache(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, AggBatchResult<D>> dbFallback,
            Long time,
            TimeUnit timeUnit
    );

    void autoClearAggCache(String singleKey);
}
