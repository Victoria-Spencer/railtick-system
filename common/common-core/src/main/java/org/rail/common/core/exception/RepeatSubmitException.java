package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 重复请求异常
 */
public class RepeatSubmitException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_CONFLICT;

    public RepeatSubmitException() {
        super(DEFAULT_CODE, "请勿重复提交");
    }

    public RepeatSubmitException(String message) {
        super(DEFAULT_CODE, message);
    }

    public RepeatSubmitException(int code, String message) {
        super(code, message);
    }

    public RepeatSubmitException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public RepeatSubmitException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public RepeatSubmitException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public RepeatSubmitException(int code, Throwable cause) {
        super(code, cause);
    }
}