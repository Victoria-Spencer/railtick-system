package org.rail.common.core.exception;

import java.io.Serial;

/**
 * 远程调用异常类：用户来远程调用失败的场景
 */
public class OpenFeignException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public OpenFeignException() {
        super();
    }

    public OpenFeignException(String message) {
        super(message);
    }

    public OpenFeignException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenFeignException(Throwable cause) {
        super(cause);
    }
}