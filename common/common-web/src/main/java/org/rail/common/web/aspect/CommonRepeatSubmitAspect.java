package org.rail.common.web.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.rail.common.core.annotation.CommonRepeatSubmit;
import org.rail.common.core.constant.AspectOrderConstants;
import org.rail.common.core.exception.RepeatSubmitException;
import org.rail.common.core.exception.RepeatSubmitTokenInvalidException;
import org.rail.common.core.exception.UserConcurrentLockException;
import org.rail.common.redis.api.ICacheClient;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


/**
 * 通用防重切面（全场景通用，无业务耦合）
 */
@Aspect
@Component
@RequiredArgsConstructor
@Order(AspectOrderConstants.REPEAT_SUBMIT)
public class CommonRepeatSubmitAspect {

    private final RedissonClient redissonClient;
    private final ICacheClient cacheClient;

    private static final String LOCK_PREFIX = "COMMON:REPEAT:LOCK:";
    private static final String TOKEN_PREFIX = "COMMON:REPEAT:TOKEN:";
    private static final String USED_FLAG = "USED";
    private static final String UNUSED_FLAG = "UNUSED";

    @Around("@annotation(commonRepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, CommonRepeatSubmit commonRepeatSubmit) throws Throwable {
        String token = getRepeatTokenFromHeader(commonRepeatSubmit);
        String lockKey = LOCK_PREFIX + token;
        String tokenKey = TOKEN_PREFIX + token;

        // 分布式锁：防瞬时并发点击
        RLock lock = redissonClient.getLock(lockKey);
        boolean lockSuccess = false;

        try {
            lockSuccess = lock.tryLock(100, commonRepeatSubmit.lockExpire(), commonRepeatSubmit.lockUnit());
            if (!lockSuccess) {
                throw new UserConcurrentLockException(commonRepeatSubmit.message());
            }

            // 校验Token是否有效/已使用
            String cacheValue = cacheClient.get(tokenKey);
            if (cacheValue == null) {
                throw new RepeatSubmitTokenInvalidException("请求令牌无效或已过期");
            }
            if (!UNUSED_FLAG.equals(cacheValue)) {
                throw new RepeatSubmitException(commonRepeatSubmit.message());
            }

            Object result = joinPoint.proceed();

            cacheClient.set(tokenKey, USED_FLAG, commonRepeatSubmit.tokenExpire(), commonRepeatSubmit.tokenUnit());

            return result;
        } finally {
            if (lockSuccess && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 从请求头获取防重Token
     */
    private String getRepeatTokenFromHeader(CommonRepeatSubmit annotation) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new RuntimeException("防重功能仅支持Web请求");
        }

        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader(annotation.headerName());
        if (token == null || token.isBlank()) {
            throw new RepeatSubmitTokenInvalidException("请求头缺少防重令牌：" + annotation.headerName());
        }
        return token;
    }
}