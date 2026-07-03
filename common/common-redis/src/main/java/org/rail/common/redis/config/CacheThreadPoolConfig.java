package org.rail.common.redis.config;

import org.rail.common.core.config.ThreadPoolConfig;
import org.rail.common.core.model.enums.RejectedPolicyEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

/**
 * 缓存专用线程池配置
 */
@Configuration
public class CacheThreadPoolConfig {

    @Value("${thread.pool.cache.core-size:5}")
    private Integer cacheCoreSize;
    @Value("${thread.pool.cache.max-size:10}")
    private Integer cacheMaxSize;
    @Value("${thread.pool.cache.keep-alive:60s}")
    private Duration cacheKeepAlive;
    @Value("${thread.pool.cache.queue-size:100}")
    private Integer cacheQueueSize;


    private final ThreadPoolConfig threadPoolConfig;

    public CacheThreadPoolConfig(ThreadPoolConfig threadPoolConfig) {
        this.threadPoolConfig = threadPoolConfig;
    }

    /**
     * 缓存重建线程池（核心任务，不丢任务）
     * 适用场景：跨模块通用核心异步任务、非模块专属的通用业务逻辑
     * 各模块核心场景请自建专有线程池，不要用公共线程池
     */
    @Bean
    public ThreadPoolTaskExecutor cacheRebuildExecutor() {
        ThreadPoolTaskExecutor executor = threadPoolConfig.buildCustomPool(
                "cache-rebuild-",
                cacheCoreSize,
                cacheMaxSize,
                cacheKeepAlive,
                cacheQueueSize,
                RejectedPolicyEnum.CALLER_RUNS.getHandler()
        );
        executor.setDaemon(false);
        return executor;
    }

}