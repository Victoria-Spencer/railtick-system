package org.rail.common.redis.constant;

public final class RedisConstants {

    private RedisConstants() {}

    /*public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;*/
    // =============== 公共常量 ================
    public static final String LOCK_PREFIX = "lock:";
    public static final Long LOCK_TTL = 10L;  // （秒）分布式锁默认过期时间，防止死锁
    public static final Long CACHE_NULL_TTL = 2L;
    public static final String DEP_PREFIX = "dep:";

    public static final Long RAIL_DEFAULT_TTL = 15L;
    public static final Long RAIL_TRAIN_BASE_CACHE_TTL = 3600L;
    public static final Long RAIL_AGG_SEAT_CLASS_CACHE_TTL_SECONDS = 5L;

    // =============== 单表根key（唯一） ================
    public static final String RAIL_PASSENGER_LIST_USER_PREFIX = "rail:passenger:list:user:"; // 乘车人列表
    public static final String RAIL_ORDER_PREFIX = "rail:order:";    //  + orderSn
    public static final String RAIL_ORDER_DETAILS_PREFIX = "rail:order:details:";

    public static final String RAIL_SELF_TICKET_PREFIX = "rail:self-ticket:";

    public static final String RAIL_TRAIN_PREFIX = "rail:train:";
    public static final String RAIL_STATION_PREFIX =  "rail:station:"; // code
    public static final String RAIL_TRAIN_STOP_STATION_PREFIX = "rail:train:stop-station:"; // trainId
    public static final String RAIL_TRAIN_TRAIN_TYPE_PREFIX = "rail:train:train-type:";

    public static final String RAIL_TRAIN_SEAT_CLASS_PREFIX = "rail:train:seat-class:";
    public static final String RAIL_SEAT_CLASS_PREFIX = "rail:seat-class:";

    // =============== 其它单表key（不唯一，查询条件动态变化） ================
    public static final String RAIL_TICKET_SELF_PAGE_PREFIX = "rail:ticket:self:page:"; // 本人车票分页

    // =============== 多表key ================
    public static final String RAIL_AGG_ORDER_PAGE_USER_PREFIX = "rail:agg:order:page:user:"; // 订单分页
    public static final String RAIL_AGG_TRAIN_BASE_INFO_PREFIX = "rail:agg:train:base:info:"; // 列车基础信息
    public static final String RAIL_AGG_SEAT_CLASS = "rail:agg:seat:class:"; // 余票信息

    // ================ 聚合缓存对应的布隆过滤器业务类型 =================
    public static final String AGG_CACHE_ORDER_BIZ_TYPE = "agg_cache:order";  // 订单模块
}
