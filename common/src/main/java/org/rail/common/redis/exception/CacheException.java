package org.rail.common.redis.exception;

import java.io.Serial;

/**
 * 缓存异常类
 */
public class CacheException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CacheException() {
        super();
    }

    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }

    public CacheException(Throwable cause) {
        super(cause);
    }
}
