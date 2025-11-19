package org.rail.orderservice.constant;

/**
 * 预订单状态
 */
public final class PreOrderStatusConstants {

    private PreOrderStatus() {}

    // 有效
    public static final Integer VALID  = 0;
    // 已过期
    public static final Integer EXPIRED = 1;
    // 已转为正式订单
    public static final Integer CONVERTED_TO_ORDER = 2;
}
