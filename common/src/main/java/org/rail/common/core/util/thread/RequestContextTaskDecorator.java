package org.rail.common.core.util.thread;

import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;

import java.util.concurrent.Callable;

/**
 * 线程池上下文装饰器：传递 RequestContext，解决 ThreadLocal 跨线程丢失
 */
public class RequestContextTaskDecorator {

    /**
     * 装饰 Runnable 任务，传递 RequestContext
     * 无返回值任务
     */
    public static Runnable decorate(Runnable runnable) {
        // 父线程：获取并拷贝上下文
        RequestContext originalContext = RequestContextHolder.getRequestContext();
        final RequestContext copyContext = buildCopyContext(originalContext);

        return () -> {
            try {
                // 子线程：将上下文注入
                if (copyContext != null) {
                    RequestContextHolder.setRequestContext(copyContext);
                }
                runnable.run();
            } finally {
                // 子线程执行完毕，清空上下文（防止线程池复用污染）
                RequestContextHolder.clearRequestContext();
            }
        };
    }

    /**
     * 装饰 Callable 任务，传递 RequestContext
     * 有返回值任务
     */
    public static <T> Callable<T> decorate(Callable<T> callable) {
        RequestContext originalContext = RequestContextHolder.getRequestContext();
        final RequestContext copyContext = buildCopyContext(originalContext);

        return () -> {
            try {
                if (copyContext != null) {
                    RequestContextHolder.setRequestContext(copyContext);
                }
                return callable.call();
            } finally {
                RequestContextHolder.clearRequestContext();
            }
        };
    }

    /**
     * 构建 RequestContext 副本
     */
    private static RequestContext buildCopyContext(RequestContext original) {
        if (original == null) {
            return null;
        }
        return RequestContext.builder()
                .startTime(original.getStartTime())
                .requestId(original.getRequestId())
                .accountId(original.getAccountId())
                .username(original.getUsername())
                .callerIp(original.getCallerIp())
                .source(original.getSource())
                .build();
    }
}