package org.rail.common.core.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String operation;
    private String requestMethod;
    private String requestUrl;
    private String requestIp;
    private String requestParam;
    private Boolean operateStatus;
    private String errorMsg;
    private Long costTime;
    private LocalDateTime createTime;

    /** 幂等去重 **/
    private Long messageId;
    /** MQ 消息发送时间 **/
    private LocalDateTime sendTime;
}