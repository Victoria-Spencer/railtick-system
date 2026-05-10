package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 用户操作中断异常
 * 分布式锁等待、业务执行被线程中断时抛出
 */
public class UserOperateInterruptedException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_INTERNAL_ERROR;

    public UserOperateInterruptedException() {
        super(DEFAULT_CODE, "操作已被中断，请重试");
    }

    public UserOperateInterruptedException(String message) {
        super(DEFAULT_CODE, message);
    }

    public UserOperateInterruptedException(int code, String message) {
        super(code, message);
    }

    public UserOperateInterruptedException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public UserOperateInterruptedException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public UserOperateInterruptedException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public UserOperateInterruptedException(int code, Throwable cause) {
        super(code, cause);
    }
}