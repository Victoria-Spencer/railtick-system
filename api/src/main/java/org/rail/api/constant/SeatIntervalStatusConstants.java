package org.rail.api.constant;

/**
 * 座位区间占用常量类
 */
public final class SeatIntervalStatusConstants {

    private SeatIntervalStatusConstants() {}

    /** 预订单锁定 **/
    public static final Integer LOCKED = 0;

    /** 正式订单已售 **/
    public static final Integer ALREADY_SOLD = 1;

    /** 已释放/取消订单 **/
    public static final Integer RELEASED = 2;
}
