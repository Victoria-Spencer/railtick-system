package org.rail.common.core.exception;

import cn.hutool.http.HttpStatus;
import org.rail.common.core.base.exception.BaseException;

import java.io.Serial;

/**
 * RabbitMQ 消息队列异常类：适用于消息发送/消费失败、连接异常、ACK失败等MQ全链路场景
 */
public class MqException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认错误码：503 服务不可用
     */
    private static final int DEFAULT_CODE = HttpStatus.HTTP_UNAVAILABLE;

    public MqException() {
        super(DEFAULT_CODE, "消息队列操作失败");
    }

    public MqException(String message) {
        super(DEFAULT_CODE, message);
    }

    public MqException(int code, String message) {
        super(code, message);
    }

    public MqException(String message, Throwable cause) {
        super(DEFAULT_CODE, message, cause);
    }

    public MqException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public MqException(Throwable cause) {
        super(DEFAULT_CODE, cause);
    }

    public MqException(int code, Throwable cause) {
        super(code, cause);
    }
}