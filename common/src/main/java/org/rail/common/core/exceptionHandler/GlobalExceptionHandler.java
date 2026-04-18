package org.rail.common.core.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.exception.*;
import org.rail.common.core.result.Result;
import org.rail.common.redis.exception.CacheException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler{

    // ===================== 自定义异常处理 =====================
    @ExceptionHandler(SeatLockFailedException.class)
    public Result<Void> handleSeatLockFailedException(SeatLockFailedException e) {
        String errorMessage = e.getMessage() == null ? "座位锁定失败" : e.getMessage();
        log.warn(errorMessage);
        return Result.error(errorMessage);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        String errorMessage = e.getMessage() == null ? "业务处理失败" : e.getMessage();
        log.error(errorMessage);
        return Result.error(errorMessage);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public Result<Void> handleOrderNotFoundException(OrderNotFoundException e) {
        String errorMessage = e.getMessage() == null ? "订单不存在" : e.getMessage();
        log.error(errorMessage);
        return Result.error(errorMessage);
    }

    @ExceptionHandler(OpenFeignException.class)
    public Result<Void> handleOpenFeignException(OpenFeignException e) {
        String errorMessage = e.getMessage() == null ? "远程服务调用失败" : e.getMessage();
        log.error(errorMessage);
        return Result.error(errorMessage);
    }

    @ExceptionHandler(CacheException.class)
    public Result<Void> handleCacheException(CacheException e) {
        String errorMessage = e.getMessage() == null ? "缓存服务异常" : e.getMessage();
        log.error(errorMessage);
        return Result.error(errorMessage);
    }

    @ExceptionHandler(UserConcurrentLockException.class)
    public Result<?> handleUserConcurrentLockException(UserConcurrentLockException e) {
        String errorMessage = e.getMessage() == null ? "用户并发操作冲突" : e.getMessage();
        log.warn(errorMessage);
        return Result.error(errorMessage);
    }

    @ExceptionHandler(UserOperateInterruptedException.class)
    public Result<?> handleUserOperateInterruptedException(UserOperateInterruptedException e) {
        String errorMessage = e.getMessage() == null ? "操作已被中断，请重试" : e.getMessage();
        log.warn(errorMessage);
        return Result.error(errorMessage);
    }

    // ===================== SpringMVC 参数校验异常 =====================
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleValidException(IllegalArgumentException e) {
        String errorMessage = e.getMessage() == null ? "参数校验异常" : e.getMessage();
        log.error(errorMessage);
        return Result.error(errorMessage);
    }

    // ===================== 全局兜底异常 =====================
    @ExceptionHandler(Exception.class)
    public Result<Void> handleGlobalException(Exception e) {
        log.error("服务器繁忙，请稍后再试", e);
        return Result.error("服务器繁忙，请稍后再试");
    }
}