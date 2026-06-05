package org.rail.common.core.annotation;

import java.lang.annotation.*;


/**
 * 操作日志注解
 * 仅允许加在 @Service 实现类的业务方法上
 * 禁止加在 Controller / Service 接口 / 工具类
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作描述/模块名称 **/
    String value();

    /** 是否保存请求参数 */
    boolean saveParam() default true;
}