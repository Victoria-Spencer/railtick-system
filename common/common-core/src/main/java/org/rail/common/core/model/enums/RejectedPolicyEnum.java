package org.rail.common.core.model.enums;

import lombok.Getter;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池拒绝策略枚举类
 * 线程池拒绝策略用于处理当线程池已满且队列已满时，提交新任务的处理方式
 */
@Getter
public enum RejectedPolicyEnum {
    /**
     * 直接抛出异常，核心场景推荐使用，快速失败避免故障扩散
     */
    ABORT(new ThreadPoolExecutor.AbortPolicy()),
    /**
     * 调用者线程执行，消费场景推荐使用，避免丢任务
     */
    CALLER_RUNS(new ThreadPoolExecutor.CallerRunsPolicy()),
    /**
     * 丢弃队列最老的任务，批量任务场景可用
     */
    DISCARD_OLDEST(new ThreadPoolExecutor.DiscardOldestPolicy()),
    /**
     * 直接丢弃当前任务，非核心定时任务推荐使用
     */
    DISCARD(new ThreadPoolExecutor.DiscardPolicy());

    private final RejectedExecutionHandler handler;

    RejectedPolicyEnum(RejectedExecutionHandler handler) {
        this.handler = handler;
    }

}