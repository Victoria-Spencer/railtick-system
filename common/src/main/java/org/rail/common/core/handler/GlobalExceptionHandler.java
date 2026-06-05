package org.rail.common.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import jakarta.validation.ConstraintViolationException;
import org.rail.common.core.base.exception.BaseException;
import org.rail.common.core.exception.*;
import org.rail.common.core.model.result.Result;
import org.rail.common.core.util.LogUtils;
import org.rail.common.redis.exception.CacheException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler{

    @ExceptionHandler(BaseException.class)
    public Result<Void> handleBaseException(BaseException e) {
        String[] location = getErrorLocation(e);
        String realErrorMsg = getErrorMessage(e, e.getMessage());

        // ====================== 前端友好提示：根据异常类型返回固定文案 ======================
        String frontMsg = switch (e) {
            case SeatLockFailedException seatLockFailedException -> "座位锁定失败，请重新选座";
            case OrderNotFoundException orderNotFoundException -> "订单不存在，请检查订单号";
            case UserConcurrentLockException userConcurrentLockException -> "操作过于频繁，请稍后重试";
            case UserOperateInterruptedException userOperateInterruptedException -> "操作已中断，请重新尝试";
            case RepeatSubmitException repeatSubmitException -> "请勿重复提交操作";
            case RepeatSubmitTokenInvalidException repeatSubmitTokenInvalidException -> "请求已过期，请刷新重试";
            case OpenFeignException openFeignException -> "服务繁忙，请稍后重试";
            case CacheException cacheException -> "缓存服务异常，请稍后重试";
            case BizException businessException -> "操作失败，请稍后重试";
            case SensitiveDataException sensitiveDataException -> "数据处理异常，请稍后重试";
            case MqException mqException -> "消息队列服务异常，请稍后重试";
            case UnauthorizedException unauthorizedException -> "未授权访问，请先登录";
            default -> "业务处理失败，请稍后重试";
        };

        LogUtils.error(location[0], location[1], realErrorMsg, e);

        return Result.error(e.getCode(), frontMsg);
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
        return Result.error(HttpStatus.HTTP_BAD_REQUEST, errorMsg);
    }

    /**
     * 处理 GET/单个参数 校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = e.getConstraintViolations().iterator().next().getMessage();
        LogUtils.warn(location[0], location[1], errorMsg, e);
        return Result.error(HttpStatus.HTTP_BAD_REQUEST, errorMsg);
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
        return Result.error(HttpStatus.HTTP_BAD_REQUEST, errorMsg);
    }

    /**
     * 处理手动抛出的参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleValidException(IllegalArgumentException e) {
        String[] location = getErrorLocation(e);
        String errorMsg = getErrorMessage(e, "参数校验异常");
        LogUtils.error(location[0], location[1], errorMsg, e);
        return Result.error(HttpStatus.HTTP_BAD_REQUEST, errorMsg);
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