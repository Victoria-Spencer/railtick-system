package org.rail.ticketservice.constant;

/**
 * 座位区间占用常量类
 */
public final class SeatIntervalStatusConstants {

    private SeatIntervalStatusConstants() {}

    /**  锁定中 **/
    public static final Integer LOCKED = 0;

    /** 已售出 **/
    public static final Integer ALREADY_SOLD = 1;

    /** 已释放 **/
    public static final Integer RELEASED = 2;
}
