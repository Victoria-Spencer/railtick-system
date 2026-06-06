package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 敏感数据处理异常
 * 用于数据加解密、脱敏等场景的业务异常
 */
public class SensitiveDataException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_BAD_REQUEST;

    private static final String DEFAULT_MESSAGE = "敏感数据处理失败";

    public SensitiveDataException() {
        super(DEFAULT_CODE, DEFAULT_MESSAGE);
    }

    public SensitiveDataException(String message) {
        super(DEFAULT_CODE, message);
    }

    public SensitiveDataException(int code, String message) {
        super(code, message);
    }

    public SensitiveDataException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public SensitiveDataException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public SensitiveDataException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public SensitiveDataException(int code, Throwable cause) {
        super(code, cause);
    }
}