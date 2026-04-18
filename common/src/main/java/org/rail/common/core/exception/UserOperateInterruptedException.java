package org.rail.common.core.exception;

/**
 * 用户操作中断异常
 * 分布式锁等待、业务执行被线程中断时抛出
 */
public class UserOperateInterruptedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UserOperateInterruptedException() {
        super("操作已被中断，请重试");
    }

    public UserOperateInterruptedException(String message) {
        super(message);
    }

    public UserOperateInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserOperateInterruptedException(Throwable cause) {
        super(cause);
    }
}