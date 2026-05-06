package org.rail.common.core.exception;

import java.io.Serial;

/**
 * 重复请求异常
 */
public class RepeatSubmitException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RepeatSubmitException() {
        super("请勿重复提交");
    }

    public RepeatSubmitException(String message) {
        super(message);
    }

    public RepeatSubmitException(String message, Throwable cause) {
        super(message, cause);
    }

    public RepeatSubmitException(Throwable cause) {
        super(cause);
    }
}