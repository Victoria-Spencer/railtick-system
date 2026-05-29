package org.rail.common.business.constant;

/**
 * 聚合查询专属Redis常量
 * 包含所有多表关联、统计查询的缓存Key
 */
public final class AggRedisConstants {

    private AggRedisConstants() {}

    public static final String RAIL_AGG_ORDER_USER_ALL_KEY = "rail:agg:order_user_all_key"; // 订单用户信息全量缓存
    public static final String RAIL_AGG_ORDER_PAGE_USER_PREFIX = "rail:agg:order:page:user:"; // 订单分页
    public static final String RAIL_AGG_TRAIN_BASE_INFO_PREFIX = "rail:agg:train:base:info:"; // 列车基础信息
    public static final String RAIL_AGG_SEAT_CLASS = "rail:agg:seat:class:"; // 余票信息

    public static final Long RAIL_TRAIN_BASE_CACHE_TTL_HOURS = 24L;
    public static final Long RAIL_AGG_SEAT_CLASS_CACHE_TTL_SECONDS = 5L;
}