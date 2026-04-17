package org.rail.common.core.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.exception.BusinessException;
import org.rail.common.core.exception.OpenFeignException;
import org.rail.common.core.exception.OrderNotFoundException;
import org.rail.common.core.exception.SeatLockFailedException;
import org.rail.common.core.result.Result;
import org.rail.common.redis.exception.CacheException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice // 标记为全局异常处理器
@Slf4j
public class GlobalExceptionHandler{

    // ===================== 自定义异常处理 =====================
    @ExceptionHandler(SeatLockFailedException.class)
    public Result<Void> handleSeatLockFailedException(SeatLockFailedException e) {
        log.warn("座位锁定失败：", e);
        return Result.error(e.getMessage() == null ? "座位锁定失败" : e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常：", e); // 打印完整堆栈
        return Result.error(e.getMessage() == null ? "业务处理失败" : e.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public Result<Void> handleOrderNotFoundException(OrderNotFoundException e) {
        log.error("订单不存在异常：", e);
        return Result.error(e.getMessage() == null ? "订单不存在" : e.getMessage());
    }

    @ExceptionHandler(OpenFeignException.class)
    public Result<Void> handleOpenFeignException(OpenFeignException e) {
        log.error("远程调用异常：", e);
        return Result.error(e.getMessage() == null ? "远程服务调用失败" : e.getMessage());
    }

    @ExceptionHandler(CacheException.class)
    public Result<Void> handleCacheException(CacheException e) {
        log.error("缓存操作异常：", e);
        return Result.error(e.getMessage() == null ? "缓存服务异常" : e.getMessage());
    }

    // ===================== SpringMVC 参数校验异常 =====================
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleValidException(IllegalArgumentException e) {
        // 获取校验失败的提示信息
        log.error("参数校验异常：", e);
        return Result.error(e.getMessage() == null ? "参数校验异常" : e.getMessage());
    }

    // ===================== 全局兜底异常（最高优先级最后） =====================
    @ExceptionHandler(Exception.class)
    public Result<Void> handleGlobalException(Exception e) {
        log.error("系统未知异常：", e);
        return Result.error("服务器繁忙，请稍后再试");
    }
}
