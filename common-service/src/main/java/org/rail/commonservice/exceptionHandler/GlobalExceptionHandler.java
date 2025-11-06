package org.rail.commonservice.exceptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.rail.commonservice.exception.BusinessException;
import org.rail.commonservice.result.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice // 标记为全局异常处理器
@Slf4j
public class GlobalExceptionHandler{

    @ExceptionHandler(value = BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage());
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(value = Exception.class)
    public Result<Void> handleGlobalException(Exception e) {
        log.error("系统异常", e); // 记录堆栈
        return Result.error(e.getMessage());
    }
}
