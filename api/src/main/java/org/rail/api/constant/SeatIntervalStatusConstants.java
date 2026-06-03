package org.rail.api.constant;

/**
 * 座位区间占用常量类
 */
public final class SeatIntervalStatusConstants {

    private SeatIntervalStatusConstants() {}

    /** 预订单临时锁定（临时） */
    public static final Integer PRE_LOCKED = 0;

    /** 正式订单临时锁定（临时） */
    public static final Integer ORDER_LOCKED = 1;

    /** 已支付/永久锁定（最终态，不可释放） */
    public static final Integer PAID = 2;

    /** 已释放/取消订单 **/
    public static final Integer RELEASED = 3;
}
