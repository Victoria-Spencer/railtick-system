package org.rail.orderservice.constant;

/**
 * 订单状态常量
 */
public final class OrderStatusConstants {

    private OrderStatusConstants() {}

    /** 未完成 */
    public static final Integer UNFINISHED = 0;

    /** 未出行 */
    public static final Integer UNTRAVELLED = 1;

    /** 历史订单 */
    public static final Integer HISTORY = 2;
}