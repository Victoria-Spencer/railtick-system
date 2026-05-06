package org.rail.common.core.exception;

import java.io.Serial;

/**
 *  令牌无效 / 过期异常
 */
public class RepeatSubmitTokenInvalidException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RepeatSubmitTokenInvalidException() {
        super("令牌无效或过期");
    }

    public RepeatSubmitTokenInvalidException(String message) {
        super(message);
    }

    public RepeatSubmitTokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public RepeatSubmitTokenInvalidException(Throwable cause) {
        super(cause);
    }
}