package org.rail.ticketservice.constant.redis;

/**
 * 座位业务专属Redis常量
 * 包含锁座、座位状态、座位信息等所有座位相关缓存Key
 */
public final class SeatRedisConstants {

    private SeatRedisConstants() {}

    public static final String RAIL_LOCK_SEAT_CACHE_INIT = "rail:lock:seat:cache:init";

    // 双位图组合状态
    /** 座位平级位图1（对应Lua脚本 bitmap1） + trainId + seatId */
    public static final String RAIL_BITMAP_SEAT_SLOT1_PREFIX = "rail:bitmap:seat:slot1:";
    /** 座位平级位图2（对应Lua脚本 bitmap2） + trainId + seatId */
    public static final String RAIL_BITMAP_SEAT_SLOT2_PREFIX = "rail:bitmap:seat:slot2:";

    // 座位信息
    public static final String RAIL_HASH_SEAT_INFO_PREFIX = "rail:hash:seat:info:"; // 座位信息Hash + trainId
}