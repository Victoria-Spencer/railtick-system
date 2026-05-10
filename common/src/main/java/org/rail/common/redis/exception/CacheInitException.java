package org.rail.common.redis.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 缓存初始化通用异常
 * 用于所有缓存预热、初始化失败场景（座位/站点/车次等全业务）
 */
public class CacheInitException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_INTERNAL_ERROR;

    public CacheInitException() {
        super(DEFAULT_CODE, "缓存初始化失败");
    }

    public CacheInitException(String message) {
        super(DEFAULT_CODE, message);
    }

    public CacheInitException(int code, String message) {
        super(code, message);
    }

    public CacheInitException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public CacheInitException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public CacheInitException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public CacheInitException(int code, Throwable cause) {
        super(code, cause);
    }
}