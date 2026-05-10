package org.rail.common.redis.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 缓存异常类
 */
public class CacheException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_INTERNAL_ERROR;

    public CacheException() {
        super(DEFAULT_CODE, "缓存操作异常");
    }

    public CacheException(String message) {
        super(DEFAULT_CODE, message);
    }

    public CacheException(int code, String message) {
        super(code, message);
    }

    public CacheException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public CacheException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public CacheException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public CacheException(int code, Throwable cause) {
        super(code, cause);
    }
}
