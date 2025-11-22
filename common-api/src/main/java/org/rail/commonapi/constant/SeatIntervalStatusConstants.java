package org.rail.commonapi.constant;

/**
 * 座位区间占用常量类
 */
public final class SeatIntervalStatusConstants {

    /**
     * 私有构造方法，防止类被实例化
     */
    private SeatIntervalStatusConstants() {}

    /**
     * 锁定中
     */
    public static final Integer LOCKED = 1;

    /**
     * 已售出
     */
    public static final Integer ALREADY_SOLD = 2;

    /**
     * 已释放
     */
    public static final Integer RELEASED = 3;
}
