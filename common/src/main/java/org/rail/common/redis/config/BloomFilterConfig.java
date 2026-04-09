package org.rail.common.redis.config;

import org.rail.common.redis.constant.RedisConstants;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器配置类
 * 初始化全部放在这里，项目启动自动创建，无需手动调用
 */
@Configuration
public class BloomFilterConfig {

    @Value("${bloom.filter.expectedInsertions:1000000}")
    private long expectedInsertions; // 预期插入数量

    @Value("${bloom.filter.fpp:0.01}")
    private double fpp; // 误判率

    /**
     * 【聚合缓存专用布隆过滤器】
     * 配置类中完成初始化，Spring 托管Bean
     */
    @Bean
    public RBloomFilter<String> aggCacheBloomFilter(RedissonClient redissonClient) {
        // 获取布隆过滤器实例
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(
                RedisConstants.BLOOM_FILTER_PREFIX + RedisConstants.AGG_CACHE_ORDER_BIZ_TYPE
        );
        // 仅在不存在时初始化（配置类完成初始化）
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(expectedInsertions, fpp);
        }
        return bloomFilter;
    }
}