package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * 订单不存在异常
 * 用于处理订单查询、操作时订单记录不存在的业务场景
 */
public class OrderNotFoundException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_CODE = HttpStatus.HTTP_NOT_FOUND;

    public OrderNotFoundException() {
        super(DEFAULT_CODE, "订单不存在");
    }

    public OrderNotFoundException(String message) {
        super(DEFAULT_CODE, message);
    }

    public OrderNotFoundException(int code, String message) {
        super(code, message);
    }

    public OrderNotFoundException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public OrderNotFoundException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public OrderNotFoundException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public OrderNotFoundException(int code, Throwable cause) {
        super(code, cause);
    }
}