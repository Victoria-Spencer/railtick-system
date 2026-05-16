package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 业务异常类：用于处理业务逻辑中的异常（如用户不存在、密码错误等）
 */
public class BizException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_BAD_REQUEST;

    public BizException() {
        super(DEFAULT_CODE, "业务异常");
    }

    public BizException(String message) {
        super(DEFAULT_CODE, message);
    }

    public BizException(int code, String message) {
        super(code, message);
    }

    public BizException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public BizException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public BizException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public BizException(int code, Throwable cause) {
        super(code, cause);
    }
}
