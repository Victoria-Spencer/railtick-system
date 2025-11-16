package org.rail.commonservice.exception;

/**
 * 远程调用异常类：用户来远程调用失败的场景
 */
public class OpenFeignException extends RuntimeException {

    //  Java 序列化机制中用于版本控制
    private static final long serialVersionUID = 1L;

    public OpenFeignException() {
        super();
    }

    public OpenFeignException(String message) {
        super(message);
    }

    public OpenFeignException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenFeignException(Throwable cause) {
        super(cause);
    }
}
