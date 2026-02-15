package org.rail.commonservice.constant;

public final class RedisConstants {

    private RedisConstants() {}

    /*public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;*/

    public static final Long RAIL_DEFAULT_TTL = 15L;
    // 乘车人列表
    public static final String RAIL_PASSENGER_LIST_USER_PREFIX = "rail:passenger:list:user:";

    // 站点信息
    public static final String RAIL_STATION_TYPE_PREFIX = "rail:station:type:";
    public static final String RAIL_STATION_KEYWORD_PREFIX = "rail:station:keyword:";
}
