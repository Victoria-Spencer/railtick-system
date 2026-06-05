package org.rail.logservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysOperationLog {

    private Long id;

    /** 操作人ID */
    private Long userId;

    /** 操作人账号 */
    private String username;

    /** 操作描述 */
    private String operation;

    /** 请求方式 */
    private String requestMethod;

    /** 请求地址 */
    private String requestUrl;

    /** 请求IP */
    private String requestIp;

    /** 请求参数 */
    private String requestParam;

    /** 操作状态 1成功 0失败 */
    private Boolean operateStatus;

    /** 异常信息 */
    private String errorMsg;

    /** 耗时 */
    private Long costTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 幂等唯一ID */
    private Long messageId;

    /** MQ 消息发送时间 **/
    private LocalDateTime sendTime;

    /** 消费时间（入库时间） */
    private LocalDateTime consumeTime;
}