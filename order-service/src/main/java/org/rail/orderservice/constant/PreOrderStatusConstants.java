package org.rail.orderservice.constant;

/**
 * 预订单状态
 */
public final class PreOrderStatusConstants {

    private PreOrderStatusConstants() {}

    /** 预订单：有效（待支付/未过期） */
    public static final Integer VALID  = 0;
    /** 预订单：已过期 */
    public static final Integer EXPIRED = 1;
    /** 预订单：已转为正式订单 */
    public static final Integer CONVERTED_TO_ORDER = 2;
}
