package org.rail.common.business.constant;

/**
 * 列车基础数据专属Redis常量
 * 包含列车、车站、经停站、座位类型等所有基础数据缓存Key
 */
public final class TrainRedisConstants {

    private TrainRedisConstants() {}

    public static final String RAIL_TRAIN_PREFIX = "rail:train:";
    public static final String RAIL_STATION_PREFIX = "rail:station:"; // code
    public static final String RAIL_TRAIN_STOP_STATION_PREFIX = "rail:train:stop-station:"; // trainId
    public static final String RAIL_TRAIN_TRAIN_TYPE_PREFIX = "rail:train:train-type:";
    public static final String RAIL_TRAIN_SEAT_CLASS_PREFIX = "rail:train:seat-class:";
    public static final String RAIL_SEAT_CLASS_PREFIX = "rail:seat-class:";
}