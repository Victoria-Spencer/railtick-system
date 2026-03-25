package org.rail.common.redis.annotation;

import java.lang.annotation.*;

/**
 * 标记单表更新/删除方法，自动清理关联的聚合缓存
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoClearAggCache {

    // 标识来源枚举
    enum KeySource {
        PARAMETER,  // 从方法参数中取（默认）
        THREAD_LOCAL // 从 ThreadLocal 中取
    }

    KeySource keySource() default KeySource.PARAMETER;

    // 当 keySource = PARAMETER 时生效
    String idField() default "id"; // 参数对象中的主键字段名

    // 当 keySource = THREAD_LOCAL 时生效
    Class<?> threadLocalType() default Long.class; // ThreadLocal 中存储的类型

    // 单表Key前缀（默认rail:）
    String singleKeyPrefix() default "rail:";
}
