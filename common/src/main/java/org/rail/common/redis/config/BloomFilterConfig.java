package org.rail.common.redis.config;

import com.alibaba.nacos.shaded.com.google.common.hash.Funnels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BloomFilterConfig {

    // 从配置文件读取参数，灵活调整
    @Value("${bloom.filter.expectedInsertions:1000000}")
    private long expectedInsertions; // 预期插入数
    @Value("${bloom.filter.fpp:0.01}")
    private double fpp; // 误判率

    @Bean
    public BloomFilter<CharSequence> globalBloomFilter() {
        // 初始化布隆过滤器（字符串类型，线程安全）
        return BloomFilter.create(
                Funnels.stringFunnel(java.nio.charset.StandardCharsets.UTF_8),
                expectedInsertions,
                fpp
        );
    }
}