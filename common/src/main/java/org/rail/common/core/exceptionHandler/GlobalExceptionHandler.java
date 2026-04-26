package org.rail.common.core.exceptionHandler;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.exception.*;
import org.rail.common.core.result.Result;
import org.rail.common.redis.exception.CacheException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler{

    /**
     * 处理座位锁定失败异常
     */
    @ExceptionHandler(SeatLockFailedException.class)
    public Result<Void> handleSeatLockFailedException(SeatLockFailedException e) {
        String errorMessage;
        Throwable cause = e.getCause();
        // 优先级：根因消息 > 当前异常消息 > 默认提示
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            errorMessage = cause.getMessage();
        } else {
            errorMessage = e.getMessage() == null ? "座位锁定失败" : e.getMessage();
        }
        log.warn(errorMessage);
        return Result.error("座位锁定失败，请重新选座");
    }

    /**
     * 处理通用业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        String errorMessage;
        Throwable cause = e.getCause();
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            errorMessage = cause.getMessage();
        } else {
            errorMessage = e.getMessage() == null ? "业务处理失败" : e.getMessage();
        }
        log.error(errorMessage);
        return Result.error("操作失败，请稍后重试");
    }

    /**
     * 处理订单不存在异常
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public Result<Void> handleOrderNotFoundException(OrderNotFoundException e) {
        String errorMessage;
        Throwable cause = e.getCause();
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            errorMessage = cause.getMessage();
        } else {
            errorMessage = e.getMessage() == null ? "订单不存在" : e.getMessage();
        }
        log.error(errorMessage);
        return Result.error("订单不存在，请检查订单号");
    }

    /**
     * 处理Feign远程服务调用异常
     */
    @ExceptionHandler(OpenFeignException.class)
    public Result<Void> handleOpenFeignException(OpenFeignException e) {
        String errorMessage;
        Throwable cause = e.getCause();
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            errorMessage = cause.getMessage();
        } else {
            errorMessage = e.getMessage() == null ? "远程服务调用失败" : e.getMessage();
        }
        log.error(errorMessage);
        return Result.error("服务繁忙，请稍后重试");
    }

    /**
     * 处理Redis缓存操作异常
     */
    @ExceptionHandler(CacheException.class)
    public Result<Void> handleCacheException(CacheException e) {
        String errorMessage;
        Throwable cause = e.getCause();
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            errorMessage = cause.getMessage();
        } else {
            errorMessage = e.getMessage() == null ? "缓存服务异常" : e.getMessage();
        }
        log.error(errorMessage);
        return Result.error("服务繁忙，请稍后重试");
    }

    /**
     * 处理用户并发操作锁冲突异常
     */
    @ExceptionHandler(UserConcurrentLockException.class)
    public Result<?> handleUserConcurrentLockException(UserConcurrentLockException e) {
        String errorMessage;
        Throwable cause = e.getCause();
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            errorMessage = cause.getMessage();
        } else {
            errorMessage = e.getMessage() == null ? "用户并发操作冲突" : e.getMessage();
        }
        log.warn(errorMessage);
        return Result.error("操作过于频繁，请稍后重试");
    }

    /**
     * 处理用户操作被中断的异常
     */
    @ExceptionHandler(UserOperateInterruptedException.class)
    public Result<?> handleUserOperateInterruptedException(UserOperateInterruptedException e) {
        String errorMessage;
        Throwable cause = e.getCause();
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            errorMessage = cause.getMessage();
        } else {
            errorMessage = e.getMessage() == null ? "操作已被中断，请重试" : e.getMessage();
        }
        log.warn(errorMessage);
        return Result.error("操作已中断，请重新尝试");
    }

    /**
     * 处理 @RequestBody DTO 校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errorMessage = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败：{}", errorMessage);
        return Result.error(errorMessage);
    }

    /**
     * 处理 GET/单个参数 校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().iterator().next().getMessage();
        log.warn("参数校验失败：{}", errorMessage);
        return Result.error(errorMessage);
    }

    /**
     * 处理 GET 请求对象绑定 校验异常
     * GET 请求 + 前端传普通参数（表单 / URL 参数） + 后端用 实体类 / DTO 对象接收（不加 @RequestBody）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errorMessage = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败：{}", errorMessage);
        return Result.error(errorMessage);
    }

    /**
     * 处理手动抛出的参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleValidException(IllegalArgumentException e) {
        String errorMessage = e.getMessage() == null ? "参数校验异常" : e.getMessage();
        log.error(errorMessage);
        return Result.error(errorMessage);
    }

    /**
     * 全局兜底异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleGlobalException(Exception e) {
        log.error("服务器繁忙，请稍后再试", e);
        return Result.error("服务器繁忙，请稍后再试");
    }
}