package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 业务异常类：用于处理业务逻辑中的异常（如用户不存在、密码错误等）
 */
public class BusinessException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_BAD_REQUEST;

    public BusinessException() {
        super(DEFAULT_CODE, "业务异常");
    }

    public BusinessException(String message) {
        super(DEFAULT_CODE, message);
    }

    public BusinessException(int code, String message) {
        super(code, message);
    }

    public BusinessException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public BusinessException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public BusinessException(int code, Throwable cause) {
        super(code, cause);
    }
}
