package org.rail.common.core.util;

public class ThreadLocalUtils {

    private static final ThreadLocal<Object> THREAD_LOCAL = new ThreadLocal<>();

    // 私有构造方法，禁止外部new实例
    private ThreadLocalUtils() {
    }

    /**
     * 从当前线程获取数据
     * @param <T> 数据类型（由调用方指定）
     * @return 线程中存储的值（未存储则返回null）
     */
    public static <T> T get() {
        return (T) THREAD_LOCAL.get();
    }

    /**
     * 向当前线程存储数据（新值会覆盖旧值）
     * @param t 要存储的值
     * @param <T> 数据类型
     */
    public static <T> void set(T t) {
        THREAD_LOCAL.set(t);
    }

    /**
     * 清空当前线程的所有数据（必须调用，避免内存泄漏）
     */
    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
