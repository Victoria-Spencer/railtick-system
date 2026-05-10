package org.rail.common.core.aspect;

import cn.hutool.json.JSONUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.constant.AspectOrderConstants;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.model.event.OperationLogEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 操作日志切面
 * 职责：仅收集日志信息、发布日志事件
 * 解耦：不依赖任何数据库/MyBatis/Mapper
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(AspectOrderConstants.OPERATION_LOG)
public class OperationLogAspect {

    /**
     * 注入Spring事件发布器
     */
    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "operation.log.exchange";
    private static final String ROUTING_KEY = "operation.log.key";

    /**
     * 切点：拦截标注 @OperationLog 注解的方法
     */
    @Pointcut("@annotation(org.rail.common.core.annotation.OperationLog)")
    public void logPointCut() {}

    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        HttpServletRequest request = getRequest();
        OperationLog annotation = getAnnotation(joinPoint);
        RequestContext context = RequestContextHolder.getRequestContext();

        String operation = annotation.value();
        String requestMethod = request != null ? request.getMethod() : "NON_HTTP";
        String requestUrl = request != null ? request.getRequestURI() : "/non-http";
        String requestIp = (context == null || context.getCallerIp().isBlank()) ? "unknown" : context.getCallerIp();
        String requestParam = annotation.saveParam() ? getRequestParam(joinPoint) : "";
        Long userId = (context != null && context.getAccountId() != null) ? Long.parseLong(context.getAccountId()) : -1L;
        String userName = (context != null) ? context.getUsername() : "匿名用户";
        boolean operateStatus = true;
        String errorMsg = null;

        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            operateStatus = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            LocalDateTime createTime = LocalDateTime.now();

            try {
                // 发布操作日志事件（异步处理，不阻塞主线程）
                OperationLogEvent event = new OperationLogEvent(
                        userId,
                        userName,
                        operation,
                        requestMethod,
                        requestUrl,
                        requestIp,
                        requestParam,
                        operateStatus,
                        errorMsg,
                        costTime,
                        createTime
                );
                rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event);
            } catch (Exception e) {
                log.error("日志MQ发送失败", e);
            }
        }
    }

    /**
     * 获取方法上的 @OperationLog 注解
     */
    private OperationLog getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getAnnotation(OperationLog.class);
    }

    /**
     * 获取 HttpServletRequest 请求对象
     */
    private HttpServletRequest getRequest() {
        RequestAttributes attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    /**
     * 解析请求参数（过滤Request对象，JSON格式化）
     */
    private String getRequestParam(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return "";
            }
            // 过滤非业务参数
            List<Object> paramList = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof HttpServletRequest))
                    .toList();
            return JSONUtil.toJsonStr(paramList);
        } catch (Exception e) {
            log.error("解析请求参数失败", e);
            return "参数解析失败";
        }
    }
}