package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 用户操作并发锁异常
 * 同一用户同时发起多个业务操作时抛出
 */
public class UserConcurrentLockException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_CONFLICT;

    public UserConcurrentLockException() {
        super(DEFAULT_CODE, "您当前有其他业务正在处理，请稍后再试！");
    }

    public UserConcurrentLockException(String message) {
        super(DEFAULT_CODE, message);
    }

    public UserConcurrentLockException(int code, String message) {
        super(code, message);
    }

    public UserConcurrentLockException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public UserConcurrentLockException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public UserConcurrentLockException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public UserConcurrentLockException(int code, Throwable cause) {
        super(code, cause);
    }
}