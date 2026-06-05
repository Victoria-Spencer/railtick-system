package org.rail.common.core.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志事件(用于异步传递日志数据)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogEvent {

    /** 操作人ID */
    private Long userId;

    /** 操作人账号 */
    private String username;

    /** 操作描述 */
    private String operation;

    /** 请求方式(GET/POST/PUT/DELETE) */
    private String requestMethod;

    /** 请求接口地址 */
    private String requestUrl;

    /** 客户端真实IP */
    private String requestIp;

    /** 请求参数(JSON格式) */
    private String requestParam;

    /** 操作状态 1-成功 0-失败 */
    private Boolean operateStatus;

    /** 错误信息(失败时记录) */
    private String errorMsg;

    /** 接口耗时(单位：毫秒) */
    private Long costTime;

    /** 日志创建时间 */
    private LocalDateTime createTime;
}