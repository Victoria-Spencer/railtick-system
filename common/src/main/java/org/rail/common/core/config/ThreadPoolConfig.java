package org.rail.common.core.config;

import jakarta.annotation.PreDestroy;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.thread.RequestContextTaskDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolConfig {

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

    // 全局静态线程计数器
    private static final AtomicInteger BUSINESS_THREAD_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger LOW_THREAD_COUNTER = new AtomicInteger(1);

    private final RequestContextTaskDecorator ctxTaskDecorator;
    private ExecutorService businessAsyncExecutor;
    private ExecutorService lowPriorityExecutor;

    public ThreadPoolConfig(RequestContextTaskDecorator ctxTaskDecorator) {
        this.ctxTaskDecorator = ctxTaskDecorator;
    }


    /**
     * 核心业务线程池（核心任务，不丢任务）
     */
    @Bean
    public ExecutorService businessAsyncExecutor() {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "business-async-" + BUSINESS_THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        this.businessAsyncExecutor = new ThreadPoolExecutor(
                BUSINESS_CORE_SIZE, BUSINESS_MAX_SIZE, BUSINESS_KEEP_ALIVE, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(BUSINESS_QUEUE_SIZE),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return this.businessAsyncExecutor;
    }

    /**
     * 低优先级线程池（非核心，可丢弃）
     */
    @Bean
    public ExecutorService lowPriorityExecutor() {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "low-priority-" + LOW_THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        this.lowPriorityExecutor = new ThreadPoolExecutor(
                LOW_CORE_SIZE, LOW_MAX_SIZE, LOW_KEEP_ALIVE, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(LOW_QUEUE_SIZE),
                threadFactory,
                new ThreadPoolExecutor.DiscardPolicy()
        );
        return this.lowPriorityExecutor;
    }

    /**
     * 执行异步任务（不支持返回值，自动传递上下文）
     */
    public void execute(ExecutorService executor, Runnable task) {
        executor.execute(ctxTaskDecorator.decorate(task));
    }

    /**
     * 执行异步任务（不支持返回值，自动传递上下文）
     */
    public void submit(ExecutorService executor, Runnable task) {
        executor.submit(ctxTaskDecorator.decorate(task));
    }

    /**
     * 提交异步任务（支持返回值，自动传递上下文）
     */
    public <T> Future<T> submit(ExecutorService executor, Callable<T> task) {
        return executor.submit(ctxTaskDecorator.decorate(task));
    }

    /**
     * 提交无返回值任务 + 自定义固定返回值 (Runnable + T result)
     */
    public <T> Future<T> submit(ExecutorService executor, Runnable task, T result) {
        return executor.submit(ctxTaskDecorator.decorate(task), result);
    }

    /**
     * 优雅关闭
     */
    @PreDestroy
    public void destroy() {
        LogUtils.info("Spring 容器关闭，开始优雅关闭所有线程池...");
        shutdownThreadPool(businessAsyncExecutor, "businessAsyncExecutor");
        shutdownThreadPool(lowPriorityExecutor, "lowPriorityExecutor");
    }

    private void shutdownThreadPool(ExecutorService executor, String poolName) {
        if (executor == null || executor.isShutdown()) return;
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LogUtils.info(poolName + " 超时未关闭，强制关闭");
                executor.shutdownNow();
            } else {
                LogUtils.info(poolName + " 优雅关闭成功");
            }
        } catch (InterruptedException e) {
            LogUtils.info(poolName + " 关闭被中断，强制关闭");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}