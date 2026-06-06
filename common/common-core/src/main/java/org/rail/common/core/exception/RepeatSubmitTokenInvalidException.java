package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 *  令牌无效 / 过期异常
 */
public class RepeatSubmitTokenInvalidException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_UNAUTHORIZED;

    public RepeatSubmitTokenInvalidException() {
        super(DEFAULT_CODE, "令牌无效或过期");
    }

    public RepeatSubmitTokenInvalidException(String message) {
        super(DEFAULT_CODE, message);
    }

    public RepeatSubmitTokenInvalidException(int code, String message) {
        super(code, message);
    }

    public RepeatSubmitTokenInvalidException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public RepeatSubmitTokenInvalidException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public RepeatSubmitTokenInvalidException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public RepeatSubmitTokenInvalidException(int code, Throwable cause) {
        super(code, cause);
    }
}