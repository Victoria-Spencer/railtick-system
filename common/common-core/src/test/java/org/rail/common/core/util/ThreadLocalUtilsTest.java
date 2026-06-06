package org.rail.common.core.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.rail.common.core.util.thread.ThreadLocalUtils;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ThreadLocalUtilsTest {

    private static final String KEY_TEST = "test_key";
    private static final String KEY_LIST = "list_key";
    private static final String TEST_VALUE = "hello_thread_local";

    @AfterEach
    void tearDown() {
        ThreadLocalUtils.removeAll();
    }

    /**
     * 测试基础set/get存取
     */
    @Test
    void testSetAndGet() {
        ThreadLocalUtils.set(KEY_TEST, TEST_VALUE);

        // 带类型的get
        String value = ThreadLocalUtils.get(KEY_TEST, String.class);
        assertEquals(TEST_VALUE, value);

        // 兼容旧方法
        String oldGetValue = ThreadLocalUtils.get(KEY_TEST);
        assertEquals(TEST_VALUE, oldGetValue);
    }

    /**
     * 测试List集合操作（getList/addToList）
     */
    @Test
    void testListOperations() {
        ThreadLocalUtils.addToList(KEY_LIST, "A");
        ThreadLocalUtils.addToList(KEY_LIST, "B");

        List<String> list = ThreadLocalUtils.getList(KEY_LIST);
        assertEquals(2, list.size());
        assertEquals("A", list.get(0));
        assertEquals("B", list.get(1));

        // 空key返回空集合（防NPE）
        List<String> emptyList = ThreadLocalUtils.getList("empty_key");
        assertTrue(emptyList.isEmpty());
    }

    /**
     * 测试删除单个key
     */
    @Test
    void testRemoveKey() {
        ThreadLocalUtils.set(KEY_TEST, TEST_VALUE);
        ThreadLocalUtils.removeKey(KEY_TEST);

        String value = ThreadLocalUtils.get(KEY_TEST, String.class);
        assertNull(value);
    }

    /**
     * 测试清空所有数据（防内存泄漏）
     */
    @Test
    void testRemoveAll() {
        ThreadLocalUtils.set(KEY_TEST, TEST_VALUE);
        ThreadLocalUtils.addToList(KEY_LIST, "A");

        // 清空
        ThreadLocalUtils.removeAll();

        // 验证全部为空
        assertNull(ThreadLocalUtils.get(KEY_TEST, String.class));
        assertTrue(ThreadLocalUtils.getList(KEY_LIST).isEmpty());
    }
}