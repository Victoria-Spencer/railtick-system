package org.rail.common.redis.core;

import cn.hutool.core.lang.TypeReference;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface RedisStrategyCache {
    <D, ID> D queryWithPassThrough(
            String keyPrefix,
            ID id,
            TypeReference<D> typeRef,
            Function<ID, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryWithPassThrough(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> Map<DTO, D> batchQueryWithPassThrough(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    );

    <D, ID> D queryWithMutex(
            String keyPrefix,
            ID id,
            TypeReference<D> typeRef,
            Function<ID, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryWithMutex(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    );

    <D, DTO> Map<DTO, D> batchQueryWithMutex(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit,
            int retryCount
    );

    <D, ID> D queryWithLogicalExpire(
            String keyPrefix,
            ID id,
            TypeReference<D> typeRef,
            Function<ID, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            DTO dto,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> Map<DTO, D> batchQueryWithLogicalExpire(
            Function<DTO, String> keyGenerator,
            List<DTO> dtos,
            TypeReference<D> typeRef,
            Function<List<DTO>, Map<DTO, D>> batchDbFallback,
            Long time,
            TimeUnit timeUnit
    );
}
