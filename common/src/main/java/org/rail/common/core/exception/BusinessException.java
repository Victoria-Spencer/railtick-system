package org.rail.common.core.exception;

import java.io.Serial;

/**
 * 业务异常类：用于处理业务逻辑中的异常（如用户不存在、密码错误等）
 */
public class BusinessException extends RuntimeException { // 继承RuntimeException，非受检异常

    @Serial
    private static final long serialVersionUID = 1L;

    public BusinessException() {
        super("业务异常");
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(Throwable cause) {
        super(cause);
    }
}
