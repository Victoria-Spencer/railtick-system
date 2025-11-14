package org.rail.commonservice.exception;

/**
 * 订单不存在异常
 * 用于处理订单查询、操作时订单记录不存在的业务场景
 */
public class OrderNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrderNotFoundException() {
        super();
    }


    public OrderNotFoundException(String message) {
        super(message);
    }

    public OrderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public OrderNotFoundException(Throwable cause) {
        super(cause);
    }
}