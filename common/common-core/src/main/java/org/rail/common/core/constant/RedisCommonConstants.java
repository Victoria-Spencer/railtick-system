package org.rail.common.core.constant;

public final class RedisCommonConstants {

    private RedisCommonConstants() {}

    public static final Long RAIL_DEFAULT_TTL = 16L;

    // ================ 聚合缓存对应的布隆过滤器类型 =================
    public static final String BLOOM_FILTER_PREFIX = "bloom:filter:";  // 布隆过滤器前缀
    public static final String AGG_CACHE_ORDER_BIZ_TYPE = "agg_cache:order";  // 订单模块
}
