package org.rail.common.core.config;

import org.rail.common.core.model.enums.RejectedPolicyEnum;
import org.rail.common.core.util.thread.RequestContextTaskDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * 线程池统一配置模板
 * 1. 提供公共默认线程池（所有模块共享非核心场景）
 * 2. 提供静态构建模板方法，各模块可快速自定义专有线程池
 * 3. 内置RequestContext上下文传递、线程命名规范等通用能力
 * 核心原则：核心场景独立线程池隔离，避免互相影响
 */
@Configuration
public class ThreadPoolConfig {

    public static final int DEFAULT_AWAIT_TERMINATION_SECONDS = 60;

    // 核心业务池
    @Value("${thread.pool.common.business.core-size:10}")
    private Integer businessCoreSize;
    @Value("${thread.pool.common.business.max-size:20}")
    private Integer businessMaxSize;
    @Value("${thread.pool.common.business.queue-size:200}")
    private Integer businessQueueSize;
    @Value("${thread.pool.common.business.keep-alive:60s}")
    private Duration businessKeepAlive;

    // 低优先级池
    @Value("${thread.pool.common.low.core-size:2}")
    private Integer lowCoreSize;
    @Value("${thread.pool.common.low.max-size:5}")
    private Integer lowMaxSize;
    @Value("${thread.pool.common.low.queue-size:500}")
    private Integer lowQueueSize;
    @Value("${thread.pool.common.low.keep-alive:60s}")
    private Duration lowKeepAlive;

    private final RequestContextTaskDecorator ctxTaskDecorator;

    public ThreadPoolConfig(RequestContextTaskDecorator ctxTaskDecorator) {
        this.ctxTaskDecorator = ctxTaskDecorator;
    }

    /**
     * 核心业务公共线程池（核心任务，不丢任务）
     * 适用场景：跨模块通用核心异步任务、非模块专属的通用业务逻辑
     * 各模块核心场景请自建专有线程池，不要用公共线程池
     */
    @Bean
    public ThreadPoolTaskExecutor businessAsyncExecutor() {
        ThreadPoolTaskExecutor executor = buildCustomPool(
                "common-business-",
                businessCoreSize,
                businessMaxSize,
                businessKeepAlive,
                businessQueueSize,
                RejectedPolicyEnum.CALLER_RUNS.getHandler()
        );
        executor.setDaemon(false);
        return executor;
    }

    /**
     * 低优先级公共线程池（非核心，可丢弃）
     * 适用场景：日志上报、Metrics统计、非核心回调等低优先级任务
     */
    @Bean
    public ThreadPoolTaskExecutor lowPriorityExecutor() {
        ThreadPoolTaskExecutor executor = buildCustomPool(
                "common-low-",
                lowCoreSize,
                lowMaxSize,
                lowKeepAlive,
                lowQueueSize,
                RejectedPolicyEnum.DISCARD.getHandler()
        );
        executor.setDaemon(true);
        return executor;
    }

    /**
     * 完全自定义线程池构建
     * 所有通过该方法创建的线程池，自动携带RequestContext上下文传递能力
     */
    public ThreadPoolTaskExecutor buildCustomPool(String threadNamePrefix,
                                                  int corePoolSize,
                                                  int maxPoolSize,
                                                  Duration keepAlive,
                                                  int queueCapacity,
                                                  RejectedExecutionHandler rejectedHandler) {
        int keepAliveSeconds = (int) keepAlive.getSeconds();
        return buildPool(threadNamePrefix, corePoolSize, maxPoolSize, keepAliveSeconds, queueCapacity, rejectedHandler);
    }

    /**
     * 通用构建逻辑，统一注入上下文装饰器
     */
    private ThreadPoolTaskExecutor buildPool(String threadNamePrefix,
                                             int corePoolSize,
                                             int maxPoolSize,
                                             int keepAliveSeconds,
                                             int queueCapacity,
                                             RejectedExecutionHandler rejectedHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(rejectedHandler);

        // 自动设置上下文装饰器，所有提交的任务自动透传 RequestContext
        executor.setTaskDecorator(ctxTaskDecorator);

        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(DEFAULT_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

}