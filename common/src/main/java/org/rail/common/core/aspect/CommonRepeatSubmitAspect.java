package org.rail.common.core.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.rail.common.core.annotation.CommonRepeatSubmit;
import org.rail.common.core.exception.RepeatSubmitException;
import org.rail.common.core.exception.RepeatSubmitTokenInvalidException;
import org.rail.common.core.exception.UserConcurrentLockException;
import org.rail.common.redis.api.ICacheClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 通用防重切面（全场景通用，无业务耦合）
 */
@Aspect
@Component
@RequiredArgsConstructor
public class CommonRepeatSubmitAspect {

    private final ICacheClient cacheClient;

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterDiscoverer = new DefaultParameterNameDiscoverer();

    private static final String LOCK_PREFIX = "COMMON:REPEAT:LOCK:";
    private static final String TOKEN_PREFIX = "COMMON:REPEAT:TOKEN:";
    private static final String USED_FLAG = "USED";

    @Around("@annotation(commonRepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, CommonRepeatSubmit commonRepeatSubmit) throws Throwable {
        // 获取防重Key（支持参数/请求头）
        String repeatKey = getRepeatKey(joinPoint, commonRepeatSubmit);
        String lockKey = LOCK_PREFIX + repeatKey;
        String tokenKey = TOKEN_PREFIX + repeatKey;

        // 第一道防线：Redis分布式锁（防瞬时重复点击）
        boolean lockSuccess = cacheClient.setIfAbsent(
                lockKey,
                "1",
                commonRepeatSubmit.lockExpire(),
                commonRepeatSubmit.lockUnit()
        );
        if (!lockSuccess) {
            throw new UserConcurrentLockException(commonRepeatSubmit.message());
        }

        try {
            // 第二道防线：校验令牌是否已使用/过期
            String cacheValue = cacheClient.get(tokenKey);
            // 令牌不存在 = 未生成/已过期
            if (cacheValue == null) {
                throw new RepeatSubmitTokenInvalidException("请求令牌无效或已过期");
            }
            // 已使用 = 重复提交
            if (USED_FLAG.equals(cacheValue)) {
                throw new RepeatSubmitException(commonRepeatSubmit.message());
            }

            // 放行执行业务
            Object result = joinPoint.proceed();

            // 执行成功 → 标记令牌已使用（安全：不删除Key）
            cacheClient.set(
                    tokenKey,
                    USED_FLAG,
                    commonRepeatSubmit.tokenExpire(),
                    commonRepeatSubmit.tokenUnit()
            );

            return result;
        } finally {
            cacheClient.delete(lockKey);
        }
    }

    /**
     * 获取防重Key（自动适配：参数SPEL / 请求头）
     */
    private String getRepeatKey(ProceedingJoinPoint joinPoint, CommonRepeatSubmit commonRepeatSubmit) {
        CommonRepeatSubmit.KeySource source = commonRepeatSubmit.keySource();

        // 模式1：从请求头获取（全局幂等）
        if (source == CommonRepeatSubmit.KeySource.HEADER) {
            return getRepeatSubmitTokenFromHeader(commonRepeatSubmit);
        }

        // 模式2：从SPEL表达式解析（业务参数）
        String spelExpression = commonRepeatSubmit.key();
        if (spelExpression.isBlank()) {
            throw new RuntimeException("防重Key不能为空");
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = parameterDiscoverer.getParameterNames(method);
        if (paramNames == null) {
            // 处理逻辑：比如抛异常、跳过解析等
            throw new IllegalStateException("无法获取方法参数名，请检查编译配置是否开启 -parameters 选项");
        }
        EvaluationContext context = new StandardEvaluationContext();

        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        return parser.parseExpression(spelExpression).getValue(context, String.class);
    }

    private static String getRepeatSubmitTokenFromHeader(CommonRepeatSubmit commonRepeatSubmit) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new RuntimeException("无法获取请求上下文，防重提交功能只能在Web请求中使用");
        }

        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader(commonRepeatSubmit.headerName());
        if (token == null || token.isBlank()) {
            throw new RuntimeException("缺少防重令牌");
        }
        return token;
    }
}