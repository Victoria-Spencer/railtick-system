package org.rail.orderservice.util;


import org.rail.common.core.util.SnowflakeIdGenerator;

/**
 * 订单编号生成工具 (业务单号专用)
 */
public class OrderSnUtil {

    private static final String PRE_ORD_PREFIX = "PRE_ORD_";
    private static final String ORD_PREFIX = "ORD_";

    /**
     * 生成预订单编号
     */
    public static String generatePreOrderSn() {
        return PRE_ORD_PREFIX + SnowflakeIdGenerator.nextId();
    }

    /**
     * 生成正式订单编号
     */
    public static String generateOrderSn() {
        return ORD_PREFIX + SnowflakeIdGenerator.nextId();
    }
}