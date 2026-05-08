package org.rail.toolservice.util;


import org.rail.common.core.util.SnowflakeIdGenerator;

/**
 * 全局防重复提交令牌工具类
 */
public class RepeatTokenUtil {

    private static final String REPEAT_TOKEN_PREFIX = "REPEAT_";

    /**
     * 生成全局唯一防重令牌
     */
    public static String generateToken() {
        return REPEAT_TOKEN_PREFIX + SnowflakeIdGenerator.nextId();
    }
}