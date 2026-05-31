package org.rail.userservice.constant;

/**
 * 用户业务专属Redis常量
 * 包含用户、乘车人相关缓存Key
 */
public final class UserRedisConstants {

    private UserRedisConstants() {}

    public static final String RAIL_PASSENGER_LIST_USER_PREFIX = "rail:passenger:list:user:"; // 乘车人列表
}