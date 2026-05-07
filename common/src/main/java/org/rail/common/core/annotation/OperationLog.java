package org.rail.common.core.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作描述/模块名称 **/
    String value();

    /** 是否保存请求参数 */
    boolean saveParam() default true;
}