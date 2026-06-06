package org.rail.common.redis.constant;

public final class RedisConstants {

    private RedisConstants() {
    }

    public static final String REDIS_LOCK_PREFIX = "lock:";
    public static final Long REDIS_CACHE_NULL_TTL = 2L;
    public static final String REDIS_DEP_PREFIX = "dep:";
}
