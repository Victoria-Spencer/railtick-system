package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 座位锁定失败专属异常
 */
public class SeatLockFailedException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_CONFLICT;

    public SeatLockFailedException() {
        super(DEFAULT_CODE, "座位已被抢占，请重新选座");
    }

    public SeatLockFailedException(String message) {
        super(DEFAULT_CODE, message);
    }

    public SeatLockFailedException(int code, String message) {
        super(code, message);
    }

    public SeatLockFailedException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public SeatLockFailedException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public SeatLockFailedException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public SeatLockFailedException(int code, Throwable cause) {
        super(code, cause);
    }
}