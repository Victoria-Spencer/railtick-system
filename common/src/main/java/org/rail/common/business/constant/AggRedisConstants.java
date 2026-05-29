package org.rail.common.business.constant;

/**
 * 聚合查询专属Redis常量
 * 包含所有多表关联、统计查询的缓存Key
 */
public final class AggRedisConstants {

    private AggRedisConstants() {}

    public static final String RAIL_AGG_ORDER_USER_ALL_PREFIX = "rail:agg:order_user_all"; // 订单用户信息全量缓存
    public static final String RAIL_AGG_SELF_TICKET_USER_ALL_PREFIX = "rail:agg:self-ticket:user:all:"; // 本人车票全量缓存
    public static final String RAIL_SELF_TICKET_PREFIX = "rail:self-ticket:"; // 本人车票
    public static final String RAIL_AGG_TRAIN_BASE_INFO_PREFIX = "rail:agg:train:base:info:"; // 列车基础信息
    public static final String RAIL_AGG_SEAT_CLASS = "rail:agg:seat:class:"; // 余票信息

    public static final Long RAIL_TRAIN_BASE_CACHE_TTL_HOURS = 24L;
    public static final Long RAIL_AGG_SEAT_CLASS_CACHE_TTL_SECONDS = 5L;
}