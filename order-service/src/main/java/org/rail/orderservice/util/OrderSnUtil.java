package org.rail.orderservice.util;


import org.rail.common.core.util.SnowflakeIdGenerator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单编号生成工具 (业务单号专用)
 */
public class OrderSnUtil {

    private static final String PRE_ORD_PREFIX = "PRE_ORD_";
    private static final String ORD_PREFIX = "ORD_";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 生成预订单编号
     */
    public static String generatePreOrderSn() {
        String date = LocalDateTime.now().format(DATE_FORMATTER);
        return PRE_ORD_PREFIX + date + SnowflakeIdGenerator.nextId();
    }

    /**
     * 生成正式订单编号
     */
    public static String generateOrderSn() {
        String date = LocalDateTime.now().format(DATE_FORMATTER);
        return ORD_PREFIX + date + SnowflakeIdGenerator.nextId();
    }
}