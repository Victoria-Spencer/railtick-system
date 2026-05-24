package org.rail.common.business.constant;

/**
 * 座位业务专属Redis常量
 * 包含锁座、座位状态、座位信息等所有座位相关缓存Key
 */
public final class SeatRedisConstants {

    private SeatRedisConstants() {}

    // ====================== 分布式锁 ======================
    public static final String RAIL_LOCK_SEAT_CACHE_INIT = "rail:lock:seat:cache:init";

    // ====================== 座位状态缓存（Bitmap + Hash）======================
    public static final String RAIL_BITMAP_SEAT_FORMAL_PREFIX = "rail:bitmap:seat:formal:"; // 正式售出座位Bitmap + trainId
    public static final String RAIL_BITMAP_SEAT_TEMP_LOCK_PREFIX = "rail:bitmap:seat:temp_lock:"; // 临时锁座Bitmap + trainId
    public static final String RAIL_HASH_SEAT_INFO_PREFIX = "rail:hash:seat:info:"; // 座位信息Hash + trainId
    public static final String RAIL_SEAT_OCCUPY_RECORD_PREFIX = "rail:seat:occupy:record:"; // 座位占用记录元数据

    // ====================== 锁座过期配置 ======================
    public static final int RAIL_SEAT_LOCK_DELAY_MINUTES = 15;
    public static final long RAIL_SEAT_OCCUPY_LOCK_EXPIRE_MINUTES = RAIL_SEAT_LOCK_DELAY_MINUTES + 5L; // 临时锁座过期时间
    public static final long RAIL_SEAT_OCCUPY_FORMAL_EXPIRE_MINUTES = 5L; // 正式订单/已释放过期时间
}