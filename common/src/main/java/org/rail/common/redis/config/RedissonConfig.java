package org.rail.common.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedissonConfig {

    @Autowired
    private RedisProperties redisProperties;
    @Autowired
    private JsonJacksonCodec redissonJsonCodec;

    /**
     * 初始化 Redisson 客户端（单节点模式）
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 设置全局JSON序列化（替换默认二进制序列化）
        config.setCodec(redissonJsonCodec);

        SingleServerConfig serverConfig = config.useSingleServer();

        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        serverConfig.setAddress(address);

        serverConfig.setDatabase(redisProperties.getDatabase());
        serverConfig.setPassword(redisProperties.getPassword());

        Duration timeout = redisProperties.getTimeout();
        serverConfig.setConnectTimeout((int) timeout.toMillis());
        serverConfig.setTimeout((int) timeout.toMillis());

        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        serverConfig.setConnectionPoolSize(pool.getMaxActive());
        serverConfig.setConnectionMinimumIdleSize(pool.getMinIdle());
        serverConfig.setIdleConnectionTimeout((int) pool.getMaxWait().toMillis());

        return Redisson.create(config);

    }
}