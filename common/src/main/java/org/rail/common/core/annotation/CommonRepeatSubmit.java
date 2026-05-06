package org.rail.common.core.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 通用防重提交注解（全场景适用：订单/表单/支付/点赞/审批）
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CommonRepeatSubmit {

    /**
     * 防重Key：SPEL表达式（参数模式必填）
     */
    String key() default "";

    /**
     * 防重Key来源：PARAMETER=参数SPEL，HEADER=请求头
     */
    KeySource keySource() default KeySource.PARAMETER;

    /**
     * 请求头名称（keySource=HEADER时必填）
     */
    String headerName() default "Repeat-Token";

    /**
     * 分布式锁过期时间（默认15秒）
     */
    long lockExpire() default 15;

    TimeUnit lockUnit() default TimeUnit.SECONDS;

    /**
     * 防重提示语
     */
    String message() default "操作频繁，请稍后再试";

    /**
     * 令牌过期时间（默认16分钟，适配订单预订单）
     */
    long tokenExpire() default 16;

    TimeUnit tokenUnit() default TimeUnit.MINUTES;

    /**
     * Key来源枚举
     */
    enum KeySource {
        PARAMETER, HEADER
    }
}