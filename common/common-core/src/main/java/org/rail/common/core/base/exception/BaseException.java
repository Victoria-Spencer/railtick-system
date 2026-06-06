package org.rail.common.core.base.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 自定义异常基类（扩展了 code 字段）
 */
@Getter
public abstract class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private final int code;
    /** 异常信息 */
    private final String msg;

    public BaseException() {
        super();
        this.code = 500;
        this.msg = "服务器异常";
    }

    public BaseException(String msg) {
        super(msg);
        this.code = 500;
        this.msg = msg;
    }

    public BaseException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public BaseException(String msg, Throwable cause) {
        super(msg, cause);
        this.code = 500;
        this.msg = msg;
    }

    public BaseException(int code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.msg = msg;
    }

    public BaseException(Throwable cause) {
        super(cause);
        this.code = 500;
        this.msg = cause.getMessage();
    }

    public BaseException(int code, Throwable cause) {
        super(cause);
        this.code = code;
        this.msg = cause.getMessage();
    }
}