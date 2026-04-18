package org.rail.common.core.exception;

import java.io.Serial;

/**
 * 用户操作并发锁异常
 * 同一用户同时发起多个业务操作时抛出
 */
public class UserConcurrentLockException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UserConcurrentLockException() {
        super("您当前有其他业务正在处理，请稍后再试！");
    }

    public UserConcurrentLockException(String message) {
        super(message);
    }

    public UserConcurrentLockException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserConcurrentLockException(Throwable cause) {
        super(cause);
    }
}