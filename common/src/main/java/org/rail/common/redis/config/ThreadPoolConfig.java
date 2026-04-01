package org.rail.common.redis.config;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

    // ====================== 缓存专用线程池配置 ======================
    @Value("${thread.pool.cache.core-size:5}")
    private Integer CACHE_CORE_SIZE;
    @Value("${thread.pool.cache.max-size:10}")
    private Integer CACHE_MAX_SIZE;
    @Value("${thread.pool.cache.queue-size:100}")
    private Integer CACHE_QUEUE_SIZE;
    @Value("${thread.pool.cache.keep-alive:60}")
    private Long CACHE_KEEP_ALIVE;

    // ====================== 核心业务线程池配置 ======================
    @Value("${thread.pool.business.core-size:10}")
    private Integer BUSINESS_CORE_SIZE;
    @Value("${thread.pool.business.max-size:20}")
    private Integer BUSINESS_MAX_SIZE;
    @Value("${thread.pool.business.queue-size:200}")
    private Integer BUSINESS_QUEUE_SIZE;
    @Value("${thread.pool.business.keep-alive:60}")
    private Long BUSINESS_KEEP_ALIVE;

    // ====================== 低优先级线程池配置 ======================
    @Value("${thread.pool.low.core-size:2}")
    private Integer LOW_CORE_SIZE;
    @Value("${thread.pool.low.max-size:5}")
    private Integer LOW_MAX_SIZE;
    @Value("${thread.pool.low.queue-size:500}")
    private Integer LOW_QUEUE_SIZE;
    @Value("${thread.pool.low.keep-alive:60}")
    private Long LOW_KEEP_ALIVE;

    private ExecutorService cacheRebuildExecutor;
    private ExecutorService businessAsyncExecutor;
    private ExecutorService lowPriorityExecutor;

    // ====================== 1. 缓存重建线程池（核心任务，不丢任务）======================
    @Bean
    public ExecutorService cacheRebuildExecutor() {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "cache-rebuild-" + new AtomicInteger(1).getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        cacheRebuildExecutor = new ThreadPoolExecutor(
                CACHE_CORE_SIZE, CACHE_MAX_SIZE, CACHE_KEEP_ALIVE, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(CACHE_QUEUE_SIZE),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行（安全）
        );
        return cacheRebuildExecutor;
    }

    // ====================== 2. 核心业务线程池（核心任务，不丢任务）======================
    @Bean
    public ExecutorService businessAsyncExecutor() {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "business-async-" + new AtomicInteger(1).getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        businessAsyncExecutor = new ThreadPoolExecutor(
                BUSINESS_CORE_SIZE, BUSINESS_MAX_SIZE, BUSINESS_KEEP_ALIVE, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(BUSINESS_QUEUE_SIZE),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行（安全）
        );
        return businessAsyncExecutor;
    }

    // ====================== 3. 低优先级线程池（非核心，可丢弃）======================
    @Bean
    public ExecutorService lowPriorityExecutor() {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "low-priority-" + new AtomicInteger(1).getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        lowPriorityExecutor = new ThreadPoolExecutor(
                LOW_CORE_SIZE, LOW_MAX_SIZE, LOW_KEEP_ALIVE, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(LOW_QUEUE_SIZE),
                threadFactory,
                new ThreadPoolExecutor.DiscardPolicy() // 拒绝策略：静默丢弃
        );
        return lowPriorityExecutor;
    }

    // ====================== 优雅关闭 ======================
    @PreDestroy
    public void destroy() {
        System.out.println("Spring 容器关闭，开始优雅关闭所有线程池...");
        shutdownThreadPool(cacheRebuildExecutor, "cacheRebuildExecutor");
        shutdownThreadPool(businessAsyncExecutor, "businessAsyncExecutor");
        shutdownThreadPool(lowPriorityExecutor, "lowPriorityExecutor");
    }

    private void shutdownThreadPool(ExecutorService executor, String poolName) {
        if (executor == null || executor.isShutdown()) return;
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println(poolName + " 超时未关闭，强制关闭");
                executor.shutdownNow();
            } else {
                System.out.println(poolName + " 优雅关闭成功");
            }
        } catch (InterruptedException e) {
            System.err.println(poolName + " 关闭被中断，强制关闭");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}