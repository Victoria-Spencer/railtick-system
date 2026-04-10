package org.rail.common.core.exception;

import java.io.Serial;

/**
 * 缓存初始化通用异常
 * 用于所有缓存预热、初始化失败场景（座位/站点/车次等全业务）
 */
public class CacheInitException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CacheInitException() {
        super("缓存初始化失败");
    }

    public CacheInitException(String message) {
        super(message);
    }

    public CacheInitException(String message, Throwable cause) {
        super(message, cause);
    }

    public CacheInitException(Throwable cause) {
        super(cause);
    }
}