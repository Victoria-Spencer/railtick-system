package org.rail.commonservice.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.reflect.MethodSignature;
import org.rail.commonservice.annotation.AutoClearAggCache;
import org.rail.commonservice.utils.CacheClient;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.rail.commonservice.utils.ThreadLocalUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * 聚合缓存自动清理切面
 * 拦截@AutoClearAggCache注解，自动清理单表关联的聚合缓存
 */
@Slf4j
@Aspect
@Component
public class AggCacheClearAspect {

    @Autowired
    private CacheClient cacheClient;

    // 切点：拦截所有标记@AutoClearAggCache的方法
    @Pointcut("@annotation(org.rail.commonservice.annotation.AutoClearAggCache)")
    public void aggCacheClearPointcut() {}

    /**
     * 后置通知：方法执行成功后自动清理缓存
     * 方法第一个参数是更新/删除的实体对象
     */
    @AfterReturning(value = "aggCacheClearPointcut()")
    public void autoClearAggCache(JoinPoint joinPoint) {
        // 1. 获取方法上的注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoClearAggCache anno = signature.getMethod().getAnnotation(AutoClearAggCache.class);
        if (anno == null) {
            log.warn("自动清理聚合缓存失败：未找到@AutoClearAggCache注解");
            return;
        }

        // 2. 根据 keySource 获取核心标识值
        Object keyValue = null;
        if (anno.keySource() == AutoClearAggCache.KeySource.PARAMETER) {
            // 从第一个参数中取主键值
            Object[] args = joinPoint.getArgs();
            if (args.length == 0 || args[0] == null) {
                log.warn("自动清理聚合缓存失败：实体参数为空");
                return;
            }
            Object entity = args[0];
            String idField = anno.idField();
            Field field = null;
            try {
                field = entity.getClass().getDeclaredField(idField);
                field.setAccessible(true);
                keyValue = field.get(entity);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                String errorMsg = String.format("从参数中获取主键值失败，实体[%s]，字段[%s]", entity.getClass().getName(), idField);
                log.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
        } else if (anno.keySource() == AutoClearAggCache.KeySource.THREAD_LOCAL) {
            // 从 ThreadLocal 中取
            keyValue = ThreadLocalUtils.get();
            if (keyValue == null) {
                log.warn("自动清理聚合缓存失败：ThreadLocal 中无数据");
                return;
            }
            // 类型校验
            if (!anno.threadLocalType().isInstance(keyValue)) {
                String errorMsg = String.format("ThreadLocal 中存储的类型[%s]与期望类型[%s]不匹配",
                        keyValue.getClass().getName(), anno.threadLocalType().getName());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
        }

        if (keyValue == null) {
            log.warn("自动清理聚合缓存失败：核心标识值为空");
            return;
        }

        // 3. 构建单表Key并清理缓存
        String singleKey = anno.singleKeyPrefix() + keyValue;
        log.debug("自动清理聚合缓存-构建单表Key：{}", singleKey);
        cacheClient.autoClearAggCache(singleKey);
    }
}