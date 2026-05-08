package org.rail.common.core.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.rail.common.core.constant.AspectOrderConstants;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.util.LogUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 全局接口耗时监控切面（所有Controller接口自动生效）
 * 功能：统计接口执行时间、打印标准monitor监控日志
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(AspectOrderConstants.TIME_COST)
public class TimeCostAspect {

    /**
     * 切点：拦截所有 @RestController 下的所有接口
     */
    @Pointcut("execution(public * org.rail..*.controller..*.*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取接口信息
        RequestContext context = RequestContextHolder.getRequestContext();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        long start = System.currentTimeMillis();
        Object result;

        try {
            result = joinPoint.proceed();

            LogUtils.monitor(context, className, methodName, start, LogUtils.SUCCESS,
                    args, result);
            return result;
        } catch (Throwable e) {
            LogUtils.monitor(context, className, methodName, start, LogUtils.FAIL,
                    args, e);
            throw e;
        }
    }
}