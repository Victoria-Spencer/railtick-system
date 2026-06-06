package org.rail.common.redis.impl;

import cn.hutool.core.lang.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rail.common.redis.impl.config.CommonRedisTestConfig;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.rail.common.redis.core.RedisStrategyCache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CommonRedisTestConfig.class)
@ActiveProfiles("test")
public class RedissonStrategyCacheTest {

    @Autowired
    private RedisStrategyCache redisStrategyCache;

    @Autowired
    private RedissonClient redissonClient;

    // 模拟DB查询函数（缓存未命中时返回数据）
    private final Function<Long, String> mockDbFunc = id -> "db-data:" + id;
    private final Function<List<Long>, Map<Long, String>> mockBatchDbFunc = ids ->
            ids.stream().collect(Collectors.toMap(id -> id, id -> "batch-db:" + id));

    @BeforeEach
    void cleanRedisTestDb() {
        redissonClient.getKeys().flushdb();
    }

    @Test
    void testQueryWithPassThrough() {
        String keyPrefix = "test:strategy:passThrough:";
        Long id = 1001L;
        TypeReference<String> typeRef = new TypeReference<>() {};

        // 第一次查询：缓存未命中 → 写正常数据
        String data1 = redisStrategyCache.queryWithPassThrough(keyPrefix, id, typeRef, mockDbFunc, 1L, TimeUnit.MINUTES);
        assertEquals("db-data:1001", data1);

        // 第二次查询：缓存命中 → 直接返回，不再查库
        String data2 = redisStrategyCache.queryWithPassThrough(keyPrefix, id, typeRef, mockDbFunc, 1L, TimeUnit.MINUTES);
        assertEquals(data1, data2);

        // 测试空值缓存：不存在的ID → 缓存空值，防止穿透
        Long nullId = 9999L;
        String nullData = redisStrategyCache.queryWithPassThrough(
                keyPrefix, nullId, typeRef, id2 -> null, 1L, TimeUnit.MINUTES
        );
        assertNull(nullData);
    }

    @Test
    void testQueryWithMutex() {
        String keyPrefix = "test:strategy:mutex:";
        Long id = 1002L;
        TypeReference<String> typeRef = new TypeReference<>() {};

        // 第一次查询：加锁查库 → 写缓存
        String data1 = redisStrategyCache.queryWithMutex(keyPrefix, id, typeRef, mockDbFunc, 1L, TimeUnit.MINUTES);
        assertEquals("db-data:1002", data1);

        // 第二次查询：缓存命中 → 无锁等待，直接返回
        String data2 = redisStrategyCache.queryWithMutex(keyPrefix, id, typeRef, mockDbFunc, 1L, TimeUnit.MINUTES);
        assertEquals(data1, data2);
    }

    @Test
    void testQueryWithLogicalExpire() throws InterruptedException {
        String keyPrefix = "test:strategy:logical:";
        Long id = 1003L;
        TypeReference<String> typeRef = new TypeReference<>() {};

        // 原子计数器，让数据库每次查询返回不同值（模拟真实业务数据更新）
        AtomicInteger dbCallCount = new AtomicInteger(0);
        Function<Long, String> dynamicDbFunc = uid -> {
            int callNum = dbCallCount.incrementAndGet();
            return "db-data-" + callNum; // 第1次查库：data-1，第2次：data-2
        };

        // 第一次查询：缓存未命中 → 同步查库，写入逻辑过期缓存（1秒过期）
        String firstData = redisStrategyCache.queryWithLogicalExpire(
                keyPrefix, id, typeRef, dynamicDbFunc, 1L, TimeUnit.SECONDS
        );
        assertEquals("db-data-1", firstData);

        // 等待缓存逻辑过期（超过1秒）
        Thread.sleep(1100);

        // 第二次查询：缓存已过期 → 立即返回【旧数据】，同时异步重建
        String secondData = redisStrategyCache.queryWithLogicalExpire(
                keyPrefix, id, typeRef, dynamicDbFunc, 1L, TimeUnit.SECONDS
        );
        assertEquals("db-data-1", secondData); // 验证：返回旧数据，不阻塞

        // 等待异步重建任务执行完成
        Thread.sleep(500);

        // 第三次查询：缓存已被异步更新 → 返回【新数据】
        String thirdData = redisStrategyCache.queryWithLogicalExpire(
                keyPrefix, id, typeRef, dynamicDbFunc, 1L, TimeUnit.SECONDS
        );
        assertEquals("db-data-2", thirdData); // 验证：异步重建成功
    }

    @Test
    void testBatchQueryWithPassThrough() {
        String keyPrefix = "test:strategy:batchPass:";
        List<Long> ids = List.of(1004L, 1005L);
        Function<Long, String> keyGenerator = id -> keyPrefix + id;
        TypeReference<String> typeRef = new TypeReference<>() {};

        // 第一次批量查询：缓存未命中 → 批量写数据
        Map<Long, String> result1 = redisStrategyCache.batchQueryWithPassThrough(
                keyGenerator, ids, typeRef, mockBatchDbFunc, 1L, TimeUnit.MINUTES
        );
        assertEquals(2, result1.size());
        assertEquals("batch-db:1004", result1.get(1004L));

        // 第二次批量查询：缓存命中 → 直接返回
        Map<Long, String> result2 = redisStrategyCache.batchQueryWithPassThrough(
                keyGenerator, ids, typeRef, mockBatchDbFunc,
                1L, TimeUnit.MINUTES
        );
        assertEquals(result1, result2);
    }

    @Test
    void testBatchQueryWithMutex() {
        String keyPrefix = "test:strategy:batchMutex:";
        List<Long> ids = List.of(2001L, 2002L, 2003L);
        Function<Long, String> keyGenerator = id -> keyPrefix + id;
        TypeReference<String> typeRef = new TypeReference<>() {};
        int retryCount = 5;

        // 第一次批量查询：缓存未命中 → 加锁、查库、批量写缓存
        Map<Long, String> result1 = redisStrategyCache.batchQueryWithMutex(
                keyGenerator, ids, typeRef, mockBatchDbFunc, 1L, TimeUnit.MINUTES, retryCount
        );
        assertEquals(3, result1.size());
        assertEquals("batch-db:2001", result1.get(2001L));
        assertEquals("batch-db:2002", result1.get(2002L));

        // 第二次批量查询：全部命中缓存
        Map<Long, String> result2 = redisStrategyCache.batchQueryWithMutex(
                keyGenerator, ids, typeRef, mockBatchDbFunc,
                1L, TimeUnit.MINUTES, retryCount
        );
        assertEquals(result1, result2);
    }

    @Test
    void testBatchQueryWithLogicalExpire() throws InterruptedException {
        String keyPrefix = "test:strategy:batchLogical:";
        List<Long> ids = List.of(3001L, 3002L);
        Function<Long, String> keyGenerator = id -> keyPrefix + id;
        TypeReference<String> typeRef = new TypeReference<>() {};

        // 动态DB函数：每次查询返回版本号递增
        AtomicInteger callCount = new AtomicInteger(0);
        Function<List<Long>, Map<Long, String>> dynamicBatchDbFunc = idList -> {
            int version = callCount.incrementAndGet(); // 批量只加1次
            return idList.stream().collect(Collectors.toMap(
                    id -> id,
                    id -> "batch-db-v" + version
            ));
        };

        // 第一次查询：缓存未命中 → 同步查库 + 写入逻辑过期缓存（1秒过期）
        Map<Long, String> firstResult = redisStrategyCache.batchQueryWithLogicalExpire(
                keyGenerator, ids, typeRef, dynamicBatchDbFunc,
                1L, TimeUnit.SECONDS
        );
        assertEquals("batch-db-v1", firstResult.get(3001L));

        // 等待缓存过期
        Thread.sleep(1100);

        // 第二次查询：缓存已过期 → 返回旧数据，异步重建
        Map<Long, String> secondResult = redisStrategyCache.batchQueryWithLogicalExpire(
                keyGenerator, ids, typeRef, dynamicBatchDbFunc,
                1L, TimeUnit.SECONDS
        );
        assertEquals("batch-db-v1", secondResult.get(3001L)); // 依旧返回旧数据

        // 等待异步重建完成
        Thread.sleep(600);

        // 第三次查询：已更新为新数据
        Map<Long, String> thirdResult = redisStrategyCache.batchQueryWithLogicalExpire(
                keyGenerator, ids, typeRef, dynamicBatchDbFunc,
                1L, TimeUnit.SECONDS
        );
        assertEquals("batch-db-v2", thirdResult.get(3001L)); // 新数据
    }
}