package org.rail.common.core.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 通用防重提交注解（全场景适用：订单/表单/支付/点赞/审批）
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CommonRepeatSubmit {

    /** 请求头名称 */
    String headerName() default "Repeat-Token";

    /** 分布式锁过期时间（防快速点击） */
    long lockExpire() default 5;

    TimeUnit lockUnit() default TimeUnit.SECONDS;

    /** 重复提示语 */
    String message() default "请勿重复提交";

    /** 令牌过期时间（和tool-service的16分钟一致） */
    long tokenExpire() default 16;

    TimeUnit tokenUnit() default TimeUnit.MINUTES;
}