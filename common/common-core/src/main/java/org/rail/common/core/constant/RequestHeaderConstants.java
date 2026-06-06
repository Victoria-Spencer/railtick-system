package org.rail.common.core.constant;

/**
 * 全局请求头常量
 */
public class RequestHeaderConstants {

    private RequestHeaderConstants() {}

    public static final String USER_ID_HEADER = "user-id";
    public static final String USER_NAME_HEADER = "user-name";
    public static final String REQUEST_ID_HEADER = "request-id";
    public static final String START_TIME_HEADER = "start-time";
    public static final String X_REAL_IP_HEADER = "X-Real-IP";
    public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    public static final String CALLER_IP_HEADER = "caller-ip";
    public static final String SOURCE_HEADER = "source";

    public static final String TOKEN_HEADER = "token";
    public static final String REPEAT_TOKEN_HEADER = "Repeat-Token";

    /** Feign 远程调用标记请求头*/
    public static final String FEIGN_REQUEST_HEADER = "feign-call";
}
