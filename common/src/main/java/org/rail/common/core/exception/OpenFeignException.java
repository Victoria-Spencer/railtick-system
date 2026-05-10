package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 远程调用异常类：用户来远程调用失败的场景
 */
public class OpenFeignException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_UNAVAILABLE;

    public OpenFeignException() {
        super(DEFAULT_CODE, "远程调用失败");
    }

    public OpenFeignException(String message) {
        super(DEFAULT_CODE, message);
    }

    public OpenFeignException(int code, String message) {
        super(code, message);
    }

    public OpenFeignException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public OpenFeignException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public OpenFeignException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public OpenFeignException(int code, Throwable cause) {
        super(code, cause);
    }
}