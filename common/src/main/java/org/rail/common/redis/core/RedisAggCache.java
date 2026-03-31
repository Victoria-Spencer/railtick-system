package org.rail.common.redis.core;

/**
 * 聚合缓存
 */
public interface RedisAggCache {

    <D, DTO> D queryAggCache(/* 参数 */);
    void autoClearAggCache(String singleKey);
}
