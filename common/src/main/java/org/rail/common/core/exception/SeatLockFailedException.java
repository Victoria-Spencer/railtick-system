package org.rail.common.core.exception;

import java.io.Serial;

/**
 * 座位锁定失败专属异常
 */
public class SeatLockFailedException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SeatLockFailedException() {
        super("座位已被抢占，请重新选座");
    }

    public SeatLockFailedException(String message) {
        super(message);
    }

    public SeatLockFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeatLockFailedException(Throwable cause) {
        super(cause);
    }
}