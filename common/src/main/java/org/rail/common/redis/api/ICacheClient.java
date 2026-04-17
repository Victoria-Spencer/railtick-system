package org.rail.common.redis.api;

import cn.hutool.core.lang.TypeReference;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface ICacheClient {
    <T> void set(String key, T value);

    <T> void set(String key, T value, Long expireTime, TimeUnit timeUnit);

    <T> void setWithLogicalExpire(String key, T value, Long expireTime, TimeUnit timeUnit);

    <T> void batchSet(Map<String, T> keyValueMap);

    <T> void batchSet(Map<String, T> keyValueMap, Long expireTime, TimeUnit timeUnit);

    <T> T get(String key);

    <T> T get(String key, Class<T> type);

    <T> Map<String, T> batchGet(Collection<String> keys);

    boolean exists(String key);

    <T> void addSetMember(String key, T value);

    <T> void addSetMembers(String key, Collection<T> values);

    <T> void addSetMemberWithExpire(String key, T value, Long expireTime, TimeUnit timeUnit);

    <T> void addSetMembersWithExpire(String key, Collection<T> values, Long expireTime, TimeUnit timeUnit);

    <T> Set<T> getSetMembers(String key);

    void setBit(String key, long offset, boolean value);

    boolean getBit(String key, long offset);

    long bitCount(String key);

    void batchSetBits(String key, Collection<Long> offsets, boolean value);

    void setRangeBits(String key, long startOffset, long endOffset, boolean value);

    void clearBitmap(String key);

    boolean isRangeAllZero(String key, long startOffset, long endOffset);

    <T> void hPut(String key, String hashKey, T value);

    <T> void hPut(String key, String hashKey, T value, Long expireTime, TimeUnit timeUnit);

    <T> void hPutAll(String key, Map<String, T> map);

    <T> void hPutAll(String key, Map<String, T> map, Long expireTime, TimeUnit timeUnit);

    <T> void hPutAllWholeExpire(String key, Map<String, T> map, Long expireTime, TimeUnit timeUnit);

    <T> T hGet(String key, String hashKey);

    <T> List<T> hMultiGet(String key, Collection<String> hashKeys);

    <T> Map<String, T> hEntries(String key);

    Set<String> hKeys(String key);

    <T> List<T> hValues(String key);

    Long hDelete(String key, String... hashKeys);

    Boolean hExists(String key, String hashKey);

    Long hIncr(String key, String hashKey, long delta);

    void delete(String key);

    void batchDelete(Collection<String> keys);

    <T> T executeLuaFile(String luaFilePath, List<Object> keys, Object... args);

    <T> T executeLuaScript(String luaScript, List<Object> keys, Object... args);

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

    <D, DTO> D queryAggCacheWithBloom(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryAggCacheWithBloom(
            String aggKey,
            TypeReference<D> typeRef,
            Function<DTO, AggCacheResult<D>> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryAggCacheWithNullCache(
            String aggKey,
            List<String> dependSingleKeys,
            TypeReference<D> typeRef,
            Function<DTO, D> dbFallback,
            DTO dto,
            Long time,
            TimeUnit timeUnit
    );

    <D, DTO> D queryAggCacheWithNullCache(
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
