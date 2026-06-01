package org.rail.common.redis.config;

import jakarta.annotation.PreDestroy;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.thread.RequestContextTaskDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存专用线程池配置
 */
@Configuration
public class CacheThreadPoolConfig {

    @Value("${thread.pool.cache.core-size:5}")
    private Integer CACHE_CORE_SIZE;
    @Value("${thread.pool.cache.max-size:10}")
    private Integer CACHE_MAX_SIZE;
    @Value("${thread.pool.cache.queue-size:100}")
    private Integer CACHE_QUEUE_SIZE;
    @Value("${thread.pool.cache.keep-alive:60}")
    private Long CACHE_KEEP_ALIVE;

    // 全局唯一线程计数器
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    private final RequestContextTaskDecorator ctxTaskDecorator;
    private ExecutorService cacheRebuildExecutor;

    public CacheThreadPoolConfig(RequestContextTaskDecorator ctxTaskDecorator) {
        this.ctxTaskDecorator = ctxTaskDecorator;
    }

    /**
     * 缓存重建线程池
     */
    @Bean
    public ExecutorService cacheRebuildExecutor() {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "cache-rebuild-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        this.cacheRebuildExecutor = new ThreadPoolExecutor(
                CACHE_CORE_SIZE,
                CACHE_MAX_SIZE,
                CACHE_KEEP_ALIVE,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(CACHE_QUEUE_SIZE),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return this.cacheRebuildExecutor;
    }

    /**
     * 执行异步任务（不支持返回值，自动传递上下文）
     */
    public void submit(Runnable task) {
        cacheRebuildExecutor.submit(ctxTaskDecorator.decorate(task));
    }

    /**
     * 优雅关闭缓存线程池
     */
    @PreDestroy
    public void destroy() {
        if (cacheRebuildExecutor == null || cacheRebuildExecutor.isShutdown()) {
            return;
        }
        try {
            LogUtils.info("缓存重建线程池 开始优雅关闭...");
            cacheRebuildExecutor.shutdown();
            // 等待5秒关闭
            if (!cacheRebuildExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cacheRebuildExecutor.shutdownNow();
                LogUtils.info("缓存重建线程池 超时未关闭，强制关闭！");
            } else {
                LogUtils.info("缓存重建线程池 优雅关闭成功");
            }
        } catch (InterruptedException e) {
            cacheRebuildExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}