package org.rail.common.core.exceptionHandler;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintViolationException;
import org.rail.common.core.exception.*;
import org.rail.common.core.result.Result;
import org.rail.common.core.util.LogUtils;
import org.rail.common.redis.exception.CacheException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler{

    /**
     * 处理座位锁定失败异常
     */
    @ExceptionHandler(SeatLockFailedException.class)
    public Result<Void> handleSeatLockFailedException(SeatLockFailedException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "座位锁定失败");
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error("座位锁定失败，请重新选座");
    }

    /**
     * 处理通用业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "业务处理失败");
        LogUtils.error(location[0], location[1], errorMsg, e);
        return Result.error("操作失败，请稍后重试");
    }

    /**
     * 处理订单不存在异常
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public Result<Void> handleOrderNotFoundException(OrderNotFoundException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "订单不存在");
        LogUtils.error(location[0], location[1], errorMsg, e);
        return Result.error("订单不存在，请检查订单号");
    }

    /**
     * 处理Feign远程服务调用异常
     */
    @ExceptionHandler(OpenFeignException.class)
    public Result<Void> handleOpenFeignException(OpenFeignException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "远程服务调用失败");
        LogUtils.error(location[0], location[1], errorMsg, e);
        return Result.error("服务繁忙，请稍后重试");
    }

    /**
     * 处理Redis缓存操作异常
     */
    @ExceptionHandler(CacheException.class)
    public Result<Void> handleCacheException(CacheException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "缓存服务异常");
        LogUtils.error(location[0], location[1], errorMsg, e);
        return Result.error("服务繁忙，请稍后重试");
    }

    /**
     * 处理用户并发操作锁冲突异常
     */
    @ExceptionHandler(UserConcurrentLockException.class)
    public Result<?> handleUserConcurrentLockException(UserConcurrentLockException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "用户并发操作冲突");
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error("操作过于频繁，请稍后重试");
    }

    /**
     * 处理用户操作被中断的异常
     */
    @ExceptionHandler(UserOperateInterruptedException.class)
    public Result<?> handleUserOperateInterruptedException(UserOperateInterruptedException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "操作已被中断，请重试");
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error("操作已中断，请重新尝试");
    }

    /**
     * 处理重复提交异常
     */
    @ExceptionHandler(RepeatSubmitException.class)
    public Result<?> handleRepeatSubmitException(RepeatSubmitException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "请勿重复提交操作");
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error(errorMsg);
    }

    /**
     * 处理防重令牌无效/过期异常
     */
    @ExceptionHandler(RepeatSubmitTokenInvalidException.class)
    public Result<?> handleRepeatSubmitTokenInvalidException(RepeatSubmitTokenInvalidException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "请求已过期，请刷新重试");
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error(errorMsg);
    }

    /**
     * 处理 @RequestBody DTO 校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String[] location = getErrorLocation(e);
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errorMsg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error(errorMsg);
    }

    /**
     * 处理 GET/单个参数 校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = e.getConstraintViolations().iterator().next().getMessage();
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error(errorMsg);
    }

    /**
     * 处理 GET 请求对象绑定 校验异常
     * GET 请求 + 前端传普通参数（表单 / URL 参数） + 后端用 实体类 / DTO 对象接收（不加 @RequestBody）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String[] location = getErrorLocation(e);
        FieldError fieldError = e.getBindingResult().getFieldError();
        String errorMsg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error(errorMsg);
    }

    /**
     * 处理手动抛出的参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleValidException(IllegalArgumentException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "参数校验异常");
        LogUtils.error(location[0], location[1], errorMsg, e);
        return Result.error(errorMsg);
    }

    /**
     * 全局兜底异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleGlobalException(Exception e) {
        String[] location = getErrorLocation(e);
        LogUtils.error(location[0], location[1], "服务器未知异常", e);
        return Result.error("服务器繁忙，请稍后再试");
    }

    /**
     * 自动获取 类名 + 方法名
     */
    private String[] getErrorLocation(Throwable e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) {
            return new String[]{"UnknownClass", "UnknownMethod"};
        }
        StackTraceElement element = stackTrace[0];
        String className = element.getClassName().substring(element.getClassName().lastIndexOf(".") + 1);
        String methodName = element.getMethodName();
        return new String[]{className, methodName};
    }

    /**
     * 获取异常错误信息
     */
    private String getErrorMessage(Throwable e, String defaultMessage) {
        if (e.getCause() != null && StrUtil.isNotBlank(e.getCause().getMessage())) {
            return e.getCause().getMessage();
        }
        return StrUtil.blankToDefault(e.getMessage(), defaultMessage);
    }
}