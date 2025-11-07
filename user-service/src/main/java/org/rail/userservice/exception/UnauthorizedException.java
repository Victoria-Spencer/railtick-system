package org.rail.userservice.exception;

/**
 * 业务异常类：用于处理业务逻辑中的异常（如用户不存在、密码错误等）
 */
public class UnauthorizedException extends RuntimeException { // 继承RuntimeException，非受检异常


    public UnauthorizedException() {
        super();
    }

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnauthorizedException(Throwable cause) {
        super(cause);
    }
}
