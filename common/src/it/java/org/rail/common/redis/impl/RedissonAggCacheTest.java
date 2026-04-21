package org.rail.common.redis.impl;

import cn.hutool.core.lang.TypeReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.rail.common.core.result.PageResult;
import org.rail.common.redis.core.RedisAggCache;
import org.rail.common.redis.result.AggBatchResult;
import org.rail.common.redis.result.AggCacheResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class RedissonAggCacheTest {

    @Autowired
    private RedisAggCache redisAggCache;

    private static final String TEST_AGG_KEY = "test:agg:order:1001";
    private static final List<String> TEST_DEPEND_KEYS = List.of("test:order:1001", "test:user:1001");
    private static final Long TEST_TIME = 1L;
    private static final TimeUnit TEST_TIME_UNIT = TimeUnit.MINUTES;
    private static final Long TEST_ID = 1001L;

    private final TypeReference<String> STR_TYPE_REF = new TypeReference<>() {};
    private final TypeReference<PageResult<String>> PAGE_TYPE_REF = new TypeReference<>() {};

    /**
     * 测试 空值缓存 + 手动传入依赖Key（正常数据）
     */
    @Test
    void testQueryAggCacheWithNullCache_NormalData() {
        Function<Long, String> dbFunc = id -> "agg-null-data:" + id;

        // 第一次：缓存未命中 → 查库写缓存
        String data = redisAggCache.queryAggCacheWithNullCache(
                TEST_AGG_KEY,
                TEST_DEPEND_KEYS,
                STR_TYPE_REF,
                dbFunc,
                TEST_ID,
                TEST_TIME,
                TEST_TIME_UNIT
        );
        assertEquals("agg-null-data:1001", data);

        // 第二次：缓存命中
        String cacheData = redisAggCache.queryAggCacheWithNullCache(
                TEST_AGG_KEY,
                TEST_DEPEND_KEYS,
                STR_TYPE_REF,
                dbFunc,
                TEST_ID,
                TEST_TIME,
                TEST_TIME_UNIT
        );
        assertEquals(data, cacheData);
    }

    /**
     * 测试 空值缓存 + 数据库无数据（缓存空值）
     */
    @Test
    void testQueryAggCacheWithNullCache_NullData() {
        // 模拟DB无数据
        Function<Long, String> nullDbFunc = id -> null;

        // 第一次：缓存未命中 → 缓存空值
        String data = redisAggCache.queryAggCacheWithNullCache(
                TEST_AGG_KEY,
                TEST_DEPEND_KEYS,
                STR_TYPE_REF,
                nullDbFunc,
                9999L,
                TEST_TIME,
                TEST_TIME_UNIT
        );
        assertNull(data);

        // 第二次：直接读取空值缓存 → 返回null
        String cacheNullData = redisAggCache.queryAggCacheWithNullCache(
                TEST_AGG_KEY,
                TEST_DEPEND_KEYS,
                STR_TYPE_REF,
                nullDbFunc,
                9999L,
                TEST_TIME,
                TEST_TIME_UNIT
        );
        assertNull(cacheNullData);
    }

    /**
     * 测试 空值缓存 + AggCacheResult自动提取依赖Key
     */
    @Test
    void testQueryAggCacheWithNullCache_AutoDepend() {
        Function<Long, AggCacheResult<String>> dbFunc = id ->
                AggCacheResult.of("agg-null-auto-data:" + id, TEST_DEPEND_KEYS);

        String data = redisAggCache.queryAggCacheWithNullCache(
                TEST_AGG_KEY,
                STR_TYPE_REF,
                dbFunc,
                TEST_ID,
                TEST_TIME,
                TEST_TIME_UNIT
        );
        assertEquals("agg-null-auto-data:1001", data);
    }

    /**
     * 测试 批量聚合缓存（缓存命中 + 未命中 + 空值）
     */
    @Test
    void testBatchQueryAggCache() {
        // 测试数据
        List<Long> ids = List.of(3001L, 3002L, 3003L);
        Function<Long, String> keyGenerator = id -> "test:agg:batch:" + id;

        // 模拟批量DB查询：返回AggBatchResult
        Function<List<Long>, AggBatchResult<String>> batchDbFunc = missIds -> {
            Map<String, String> dataMap = missIds.stream()
                    .collect(Collectors.toMap(
                            keyGenerator::apply,
                            id -> "batch-agg-data:" + id
                    ));
            return AggBatchResult.of(dataMap, List.of("test:batch:order"));
        };

        // 执行批量查询
        List<String> result = redisAggCache.batchQueryAggCache(
                keyGenerator,
                ids,
                STR_TYPE_REF,
                batchDbFunc,
                TEST_TIME,
                TEST_TIME_UNIT
        );

        // 断言
        assertEquals(3, result.size());
        assertEquals("batch-agg-data:3001", result.get(0));
        assertEquals("batch-agg-data:3002", result.get(1));
    }

    /**
     * 测试 自动清理：单表Key变更 → 清理关联聚合缓存
     */
    @Test
    void testAutoClearAggCache() {
        String singleKey = "test:order:1001";
        String aggKey = "test:agg:order:1001";

        // 先写入聚合缓存（建立依赖关系）
        Function<Long, String> dbFunc = id -> "clear-test-data:" + id;
        redisAggCache.queryAggCacheWithNullCache(
                aggKey,
                List.of(singleKey),
                STR_TYPE_REF,
                dbFunc,
                TEST_ID,
                TEST_TIME,
                TEST_TIME_UNIT
        );

        // 执行自动清理
        assertDoesNotThrow(() -> redisAggCache.autoClearAggCache(singleKey));

        // 清理后查询 → 缓存已删除
        String clearedData = redisAggCache.queryAggCacheWithNullCache(
                aggKey,
                List.of(singleKey),
                STR_TYPE_REF,
                id -> null,
                TEST_ID,
                TEST_TIME,
                TEST_TIME_UNIT
        );
        assertNull(clearedData);
    }

    /**
     * 测试：空分页结果 → 缓存空值
     */
    @Test
    void testQueryAggCache_EmptyPage() {
        // 模拟空分页结果
        Function<Long, PageResult<String>> emptyPageFunc = id -> new PageResult<>(0L, Collections.emptyList(), 10);

        PageResult<String> result = redisAggCache.queryAggCacheWithNullCache(
                "test:agg:page:1001",
                TEST_DEPEND_KEYS,
                PAGE_TYPE_REF,
                emptyPageFunc,
                TEST_ID,
                TEST_TIME,
                TEST_TIME_UNIT
        );
        assertNull(result);
    }
}