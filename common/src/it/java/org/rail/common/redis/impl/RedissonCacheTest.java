package org.rail.common.redis.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.rail.common.redis.core.RedisCache;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class RedissonCacheTest {

    @Autowired
    private RedisCache redisCache;

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

    // ======================== Hash 类型 ========================
    @Test
    void testHashOperate() {
        String key = "test:hash:main";
        // 单个字段
        redisCache.hPut(key, "name", "zhangsan", 1L, TimeUnit.MINUTES);
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
        assertEquals("5", redisCache.hGet(key, "count"));

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
    void testLuaExecute() {
        assertDoesNotThrow(() -> {
            redisCache.executeLuaScript("return 1", List.of("test:lua:script"));
        });
    }
}