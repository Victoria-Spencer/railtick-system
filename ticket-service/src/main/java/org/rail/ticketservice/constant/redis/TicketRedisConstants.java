package org.rail.ticketservice.constant.redis;

/**
 * 票务业务专属Redis常量
 * 包含车票分页等所有票务相关缓存Key
 */
public final class TicketRedisConstants {

    private TicketRedisConstants() {}

    public static final String RAIL_AGG_TRAIN_BASE_INFO_PREFIX = "rail:agg:train:base:info:"; // 列车基础信息
    public static final String RAIL_AGG_SEAT_CLASS = "rail:agg:seat:class:"; // 余票信息
    public static final Long RAIL_TRAIN_BASE_CACHE_TTL_HOURS = 24L;
    public static final Long RAIL_AGG_SEAT_CLASS_CACHE_TTL_SECONDS = 5L;
}