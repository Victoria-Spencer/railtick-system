package org.rail.common.redis.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rail.common.redis.impl.config.CommonRedisTestConfig;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.rail.common.redis.core.RedisCache;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CommonRedisTestConfig.class)
@ActiveProfiles("test")
public class RedissonCacheTest {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void cleanRedisTestDb() {
        redissonClient.getKeys().flushdb();
    }

    // ======================== String 类型 ========================
    @Test
    void testStringSetAndGet() {
        String key = "test:string:basic";
        String value = "hello";
        redisCache.set(key, value);
        assertEquals(value, redisCache.get(key, String.class));

        String expireKey = "test:string:expire";
        redisCache.set(expireKey, value, 10L, TimeUnit.SECONDS);
        assertEquals(value, redisCache.get(expireKey, String.class));
    }

    @Test
    void testStringNullValue() {
        String key = "test:string:null";
        redisCache.set(key, null);
        assertNull(redisCache.get(key, String.class));
    }

    @Test
    void testStringLogicalExpire() {
        String key = "test:string:logical";
        redisCache.setWithLogicalExpire(key, "test-data", 10L, TimeUnit.SECONDS);
        assertTrue(redisCache.exists(key));
    }

    @Test
    void testSetIfAbsent() {
        String key = "test:setIfAbsent:normal";
        String value = "absent-value";

        // 第一次设置：key不存在，应该成功
        Boolean firstSet = redisCache.setIfAbsent(key, value);
        assertTrue(firstSet);
        assertEquals(value, redisCache.get(key, String.class));

        // 第二次设置：key已存在，应该失败
        Boolean secondSet = redisCache.setIfAbsent(key, "new-value");
        assertFalse(secondSet);
        // 值不会被覆盖
        assertEquals(value, redisCache.get(key, String.class));

        // 测试空值/空key：直接返回false
        assertFalse(redisCache.setIfAbsent("", value));
        assertFalse(redisCache.setIfAbsent(key, null));
    }

    @Test
    void testSetIfAbsentWithExpire() {
        String key = "test:setIfAbsent:expire";
        String value = "expire-value";

        // 第一次带过期时间设置：成功
        Boolean firstSet = redisCache.setIfAbsent(key, value, 30L, TimeUnit.SECONDS);
        assertTrue(firstSet);
        assertEquals(value, redisCache.get(key, String.class));

        // 重复设置：失败
        Boolean secondSet = redisCache.setIfAbsent(key, "new-value", 30L, TimeUnit.SECONDS);
        assertFalse(secondSet);

        // 非法参数校验：过期时间<=0 / 时间单位为null
        assertFalse(redisCache.setIfAbsent("test:invalid", "v", 0L, TimeUnit.SECONDS));
        assertFalse(redisCache.setIfAbsent("test:invalid", "v", -10L, TimeUnit.SECONDS));
        assertFalse(redisCache.setIfAbsent("test:invalid", "v", 10L, null));
    }

    @Test
    void testStringBatchOperate() {
        String keyPrefix = "test:string:batch:";
        Map<String, String> map = Map.of(keyPrefix + "1", "v1", keyPrefix + "2", "v2");
        redisCache.batchSet(map, 1L, TimeUnit.MINUTES);
        Map<String, String> result = redisCache.batchGet(map.keySet());
        assertEquals(2, result.size());
    }

    // ======================== Set 类型 ========================
    @Test
    void testSetOperate() {
        String key = "test:set:normal";
        redisCache.addSetMember(key, "m1");
        redisCache.addSetMembers(key, List.of("m2", "m3"));

        String expireKey = "test:set:expire";
        redisCache.addSetMembersWithExpire(expireKey, List.of("m4"), 10L, TimeUnit.SECONDS);

        Set<String> members = redisCache.getSetMembers(key);
        assertEquals(3, members.size());
        assertTrue(members.contains("m1"));
    }

    // ======================== Bitmap ========================
    @Test
    void testBitmapOperate() {
        // 单个位
        String key1 = "test:bitmap:single";
        redisCache.setBit(key1, 10, true);
        assertTrue(redisCache.getBit(key1, 10));
        assertEquals(1, redisCache.bitCount(key1));

        // 批量位
        String key2 = "test:bitmap:batch";
        redisCache.batchSetBits(key2, List.of(11L, 12L), true);
        assertEquals(2, redisCache.bitCount(key2));

        // 区间位
        String key3 = "test:bitmap:range";
        redisCache.setRangeBits(key3, 0, 5, true);
        assertFalse(redisCache.isRangeAllZero(key3, 0, 5));
        assertEquals(5, redisCache.bitCount(key3));

        // 清空
        String key4 = "test:bitmap:clear";
        redisCache.setBit(key4, 1, true);
        redisCache.clearBitmap(key4);
        assertFalse(redisCache.exists(key4));
    }

    /**
     * 验证 区间bitCount(startOffset, endOffset) 左闭右开规则
     * 严格适配Lua脚本实现
     */
    @Test
    void testBitmapBitCountRange() {
        String key = "test:bitmap:range:count";
        // 设置位：1、2、3、4 为1（共4位）
        redisCache.setRangeBits(key, 1, 5, true);
        // 设置位：6 为1
        redisCache.setBit(key, 6, true);

        // 测试1：[1,5) → 4个1
        assertEquals(4, redisCache.bitCount(key, 1, 5));
        // 测试2：[1,6) → 4个1（位4为0）
        assertEquals(4, redisCache.bitCount(key, 1, 6));
        // 测试3：[5,7) → 1个1（位6为1）
        assertEquals(1, redisCache.bitCount(key, 5, 7));
        // 测试4：非法区间 → 返回0
        assertEquals(-1, redisCache.bitCount(key, 10, 5));
        assertEquals(-1, redisCache.bitCount(key, -1, 5));
    }

    // ======================== Hash 类型 ========================
    @Test
    void testHashOperate() {
        String key = "test:hash:main";
        // 单个字段
        redisCache.hPut(key, "name", "zhangsan", 10L, TimeUnit.MINUTES);
        assertEquals("zhangsan", redisCache.hGet(key, "name"));

        // 批量字段
        Map<String, String> map = Map.of("age", "20", "gender", "male");
        redisCache.hPutAll(key, map);

        // 批量获取
        List<String> values = redisCache.hMultiGet(key, List.of("name", "age"));
        assertEquals(2, values.size());

        // 全部操作
        Map<String, String> entries = redisCache.hEntries(key);
        assertTrue(entries.containsKey("age"));
        assertFalse(redisCache.hKeys(key).isEmpty());

        // 自增
        redisCache.hIncr(key, "count", 5);
        assertEquals(Integer.valueOf(5), redisCache.hGet(key, "count"));

        // 存在&删除
        assertTrue(redisCache.hExists(key, "name"));
        redisCache.hDelete(key, "gender");
        assertNull(redisCache.hGet(key, "gender"));
    }

    // ======================== 通用删除/存在 ========================
    @Test
    void testDeleteAndExist() {
        String key = "test:common:del";
        redisCache.set(key, "123");
        assertTrue(redisCache.exists(key));

        // 单删
        redisCache.delete(key);
        assertFalse(redisCache.exists(key));

        // 批量删
        String batchKey1 = "test:common:batch:1";
        String batchKey2 = "test:common:batch:2";
        redisCache.batchSet(Map.of(batchKey1, "v1", batchKey2, "v2"));
        redisCache.batchDelete(List.of(batchKey1, batchKey2));
        assertFalse(redisCache.exists(batchKey1));
    }

    // ======================== Lua 脚本 ========================
    @Test
    void testLuaScriptDirectExecute() {
        assertDoesNotThrow(() -> {
            redisCache.executeLuaScript("return 1", List.of("test:lua:script"));
        });
    }

    @Test
    void testLuaNumericParamWithInt() {
        // int类型
        int numParam = 888;

        Long retCode = redisCache.executeLuaFile(
                "lua/numeric_param_test.lua",
                Collections.emptyList(),
                numParam
        );

        assertEquals(889, retCode);
    }

    @Test
    void testLuaNumericParamWithLong() {
        // long
        long numParam = 888L;

        Long retCode = redisCache.executeLuaFile(
                "lua/numeric_param_test.lua",
                Collections.emptyList(),
                numParam
        );

        assertEquals(889, retCode);
    }

    @Test
    void testLuaStringParamWithNormalValue() {
        String strParam = "hello-redis";

        Long retCode = redisCache.executeLuaFile(
                "lua/string_test.lua",
                Collections.emptyList(),
                strParam
        );

        // hello-redis 长度 = 11
        assertEquals(11, retCode);
    }

    @Test
    void testLuaStringParamWithEmptyValue() {
        String strParam = "";

        Long retCode = redisCache.executeLuaFile(
                "lua/string_test.lua",
                Collections.emptyList(),
                strParam
        );

        // 空字符串 → 返回0
        assertEquals(0, retCode);
    }
}