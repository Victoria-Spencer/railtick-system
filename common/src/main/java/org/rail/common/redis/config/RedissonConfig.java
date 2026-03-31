package org.rail.common.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedissonConfig {

    // 直接注入SpringBoot自动封装的Redis配置类
    @Autowired
    private RedisProperties redisProperties;

    @Bean
    public RedissonClient redissonClient() {
        // 单节点配置
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer();

        // 1. 拼接Redis地址
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        serverConfig.setAddress(address);

        // 2. 数据库索引、密码
        serverConfig.setDatabase(redisProperties.getDatabase());
        // 无密码自动传null，有密码自动读取
        serverConfig.setPassword(redisProperties.getPassword());

        // 3. 超时时间
        Duration timeout = redisProperties.getTimeout();
        serverConfig.setConnectTimeout((int) timeout.toMillis());
        serverConfig.setTimeout((int) timeout.toMillis());

        // 4. 连接池
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        serverConfig.setConnectionPoolSize(pool.getMaxActive());
        serverConfig.setConnectionMinimumIdleSize(pool.getMinIdle());
        serverConfig.setIdleConnectionTimeout((int) pool.getMaxWait().toMillis());

        return Redisson.create(config);

    }
}