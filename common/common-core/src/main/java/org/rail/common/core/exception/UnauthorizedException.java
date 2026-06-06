package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 业务异常类：用于处理业务逻辑中的异常（如用户不存在、密码错误等）
 */
public class UnauthorizedException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_UNAUTHORIZED;

    public UnauthorizedException() {
        super(DEFAULT_CODE, "未授权访问，请先登录");
    }

    public UnauthorizedException(String message) {
        super(DEFAULT_CODE, message);
    }

    public UnauthorizedException(int code, String message) {
        super(code, message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public UnauthorizedException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public UnauthorizedException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public UnauthorizedException(int code, Throwable cause) {
        super(code, cause);
    }
}
