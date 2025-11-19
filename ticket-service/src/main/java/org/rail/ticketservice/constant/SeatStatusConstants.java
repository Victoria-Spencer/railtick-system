package org.rail.ticketservice.constant;

/**
 * 座位状态常量类
 * 统一管理座位的各类状态定义
 */
public final class SeatStatusConstants {

    /**
     * 私有构造方法，防止类被实例化
     */
    private SeatStatusConstants() {}

    /**
     * 完全可选（座位无任何占用区间）
     */
    public static final Integer AVAILABLE = 0;

    /**
     * 部分区间占用（座位有部分区间被占用，仍有可选区间）
     */
    public static final Integer PARTIALLY_OCCUPIED = 1;

    /**
     * 全区间已售（座位所有区间均被占用，无可选区间）
     */
    public static final Integer FULLY_OCCUPIED = 2;
}
