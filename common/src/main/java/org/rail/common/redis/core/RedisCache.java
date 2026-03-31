package org.rail.common.redis.core;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 底层基础方法
 */
public interface RedisCache {
    <T> void set(String key, T value);

    <T> void set(String key, T value, Long expireTime, TimeUnit timeUnit);

    <T> void setWithLogicalExpire(String key, T value, Long expireTime, TimeUnit timeUnit);

    <T> void batchSet(Map<String, T> keyValueMap);

    <T> void batchSet(Map<String, T> keyValueMap, Long expireTime, TimeUnit timeUnit);

    <T> T get(String key);

    <T> Map<String, T> batchGet(Collection<String> keys);

    <T> void addSetMember(String key, T value);

    <T> void addSetMembers(String key, Collection<T> values);

    <T> Set<T> getSetMembers(String key);

    void delete(String key);

    void batchDelete(Collection<String> keys);

    boolean exists(String key);
}
