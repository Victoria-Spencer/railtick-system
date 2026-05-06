package org.rail.common.redis.impl;

import cn.hutool.core.lang.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rail.common.core.result.PageResult;
import org.rail.common.redis.core.RedisAggCache;
import org.rail.common.redis.impl.config.CommonTestConfig;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CommonTestConfig.class)
@ActiveProfiles("test")
public class RedissonAggCacheTest {

    @Autowired
    private RedisAggCache redisAggCache;

    @Autowired
    private RedissonClient redissonClient;

    private static final Long TEST_TIME = 1L;
    private static final TimeUnit TEST_TIME_UNIT = TimeUnit.MINUTES;
    private static final Long TEST_ID = 1001L;

    private final TypeReference<String> STR_TYPE_REF = new TypeReference<>() {};
    private final TypeReference<PageResult<String>> PAGE_TYPE_REF = new TypeReference<>() {};

    @BeforeEach
    void cleanRedisTestDb() {
        redissonClient.getKeys().flushdb();
    }

    /**
     * 测试 空值缓存 + 手动传入依赖Key（正常数据）
     */
    @Test
    void testQueryAggCacheWithNullCache_NormalData() {
        String aggKey = "test:agg:normal:1001";
        List<String> dependKeys = List.of("test:order:normal:1001", "test:user:normal:1001");

        Function<Long, String> dbFunc = id -> "agg-null-data:" + id;

        String data = redisAggCache.queryAggCacheWithNullCache(
                aggKey, dependKeys, STR_TYPE_REF, dbFunc, TEST_ID, TEST_TIME, TEST_TIME_UNIT
        );
        assertEquals("agg-null-data:1001", data);

        String cacheData = redisAggCache.queryAggCacheWithNullCache(
                aggKey, dependKeys, STR_TYPE_REF, dbFunc, TEST_ID, TEST_TIME, TEST_TIME_UNIT
        );
        assertEquals(data, cacheData);
    }

    /**
     * 测试 空值缓存 + 数据库无数据（缓存空值）
     */
    @Test
    void testQueryAggCacheWithNullCache_NullData() {
        String aggKey = "test:agg:null:1001";
        List<String> dependKeys = List.of("test:order:null:1001", "test:user:null:1001");

        Function<Long, String> nullDbFunc = id -> null;

        String data = redisAggCache.queryAggCacheWithNullCache(
                aggKey, dependKeys, STR_TYPE_REF, nullDbFunc, 9999L, TEST_TIME, TEST_TIME_UNIT
        );
        assertNull(data);

        String cacheNullData = redisAggCache.queryAggCacheWithNullCache(
                aggKey, dependKeys, STR_TYPE_REF, nullDbFunc, 9999L, TEST_TIME, TEST_TIME_UNIT
        );
        assertNull(cacheNullData);
    }

    /**
     * 测试 空值缓存 + AggCacheResult自动提取依赖Key
     */
    @Test
    void testQueryAggCacheWithNullCache_AutoDepend() {
        String aggKey = "test:agg:auto:1001";
        List<String> dependKeys = List.of("test:order:auto:1001", "test:user:auto:1001");

        Function<Long, AggCacheResult<String>> dbFunc = id -> AggCacheResult.of("agg-null-auto-data:" + id, dependKeys);

        String data = redisAggCache.queryAggCacheWithNullCache(
                aggKey, STR_TYPE_REF, dbFunc, TEST_ID, TEST_TIME, TEST_TIME_UNIT
        );
        assertEquals("agg-null-auto-data:1001", data);
    }

    /**
     * 测试 批量聚合缓存（缓存命中 + 未命中 + 空值）
     */
    @Test
    void testBatchQueryAggCache() {
        List<Long> ids = List.of(3001L, 3002L, 3003L);
        Function<Long, String> keyGenerator = id -> "test:agg:batch:" + id;

        Function<List<Long>, AggBatchResult<String>> batchDbFunc = missIds -> {
            Map<String, String> dataMap = missIds.stream()
                    .collect(Collectors.toMap(keyGenerator::apply, id -> "batch-agg-data:" + id));
            return AggBatchResult.of(dataMap, List.of("test:batch:order"));
        };

        List<String> result = redisAggCache.batchQueryAggCache(
                keyGenerator, ids, STR_TYPE_REF, batchDbFunc, TEST_TIME, TEST_TIME_UNIT
        );

        assertEquals(3, result.size());
        assertEquals("batch-agg-data:3001", result.get(0));
        assertEquals("batch-agg-data:3002", result.get(1));
    }

    /**
     * 测试 自动清理：单表Key变更 → 清理关联聚合缓存
     */
    @Test
    void testAutoClearAggCache() {
        String singleKey = "test:order:clear:1001";
        String aggKey = "test:agg:clear:1001";

        Function<Long, String> dbFunc = id -> "clear-test-data:" + id;
        redisAggCache.queryAggCacheWithNullCache(
                aggKey, List.of(singleKey), STR_TYPE_REF, dbFunc, TEST_ID, TEST_TIME, TEST_TIME_UNIT
        );

        assertDoesNotThrow(() -> redisAggCache.autoClearAggCache(singleKey));

        String clearedData = redisAggCache.queryAggCacheWithNullCache(
                aggKey, List.of(singleKey), STR_TYPE_REF, id -> null, TEST_ID, TEST_TIME, TEST_TIME_UNIT
        );
        assertNull(clearedData);
    }

    /**
     * 测试：空分页结果 → 缓存空值
     */
    @Test
    void testQueryAggCache_EmptyPage() {
        String aggKey = "test:agg:page:1001";
        List<String> dependKeys = List.of("test:order:page:1001", "test:user:page:1001");

        Function<Long, PageResult<String>> emptyPageFunc = id -> new PageResult<>(0L, Collections.emptyList(), 10);

        PageResult<String> result = redisAggCache.queryAggCacheWithNullCache(
                aggKey, dependKeys, PAGE_TYPE_REF, emptyPageFunc, TEST_ID, TEST_TIME, TEST_TIME_UNIT
        );
        assertNull(result);
    }
}