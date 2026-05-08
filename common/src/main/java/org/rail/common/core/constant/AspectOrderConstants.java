package org.rail.common.core.constant;

/**
 * 全局切面执行顺序常量
 * 数值越小，优先级越高
 */
public final class AspectOrderConstants {

    private AspectOrderConstants() {
    }

    // 限流切面
    public static final int RATE_LIMIT = 1;
    // 接口耗时监控切面
    public static final int TIME_COST = 2;
    // 操作日志切面
    public static final int OPERATION_LOG = 3;
    // 防重复提交切面
    public static final int REPEAT_SUBMIT = 4;
}