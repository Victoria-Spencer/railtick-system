package org.rail.common.core.util.thread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThreadLocalUtils {

    // 存储 Map<String, Object>
    private static final ThreadLocal<Map<String, Object>> THREAD_LOCAL =
            ThreadLocal.withInitial(HashMap::new);

    private ThreadLocalUtils() {
    }

    /**
     * 按key存储数据
     */
    public static void set(String key, Object value) {
        Map<String, Object> map = THREAD_LOCAL.get();
        map.put(key, value);
    }

    /**
     * 按key获取数据
     */
    @Deprecated
    public static <T> T get(String key) {
        Map<String, Object> map = THREAD_LOCAL.get();
        return (T) map.get(key);
    }

    /**
     * 按key获取数据
     */
    public static <T> T get(String key, Class<T> clazz) {
        Map<String, Object> map = THREAD_LOCAL.get();
        Object value = map.get(key);
        return clazz.cast(value);
    }

    /**
     * 获取List类型数据，为空则返回空集合（防NPE）
     */
    public static <T> List<T> getList(String key) {
        Map<String, Object> map = THREAD_LOCAL.get();
        Object value = map.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        return (List<T>) value;
    }

    /**
     * 往ThreadLocal的List中添加元素
     */
    public static <T> void addToList(String key, T element) {
        List<T> list = getList(key);
        list.add(element);
        set(key, list);
    }

    /**
     * 删除指定key的数据
     */
    public static void removeKey(String key) {
        Map<String, Object> map = THREAD_LOCAL.get();
        map.remove(key);
    }

    /**
     * 清空当前线程的所有数据
     */
    public static void removeAll() {
        THREAD_LOCAL.remove();
    }
}
