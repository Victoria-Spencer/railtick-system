package org.rail.common.core.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 12306 请求上下文（全链路追踪 + 用户信息）
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 请求开始时间戳（计算接口耗时） */
    private long startTime;

    /** 请求唯一标识 */
    @JsonProperty("request_id")
    private String requestId;

    /** 用户id */
    @JsonProperty("account_id")
    private String accountId;

    /** 用户名 */
    private String username;

    /** 调用方IP（风控/排查） */
    @JsonProperty("caller_ip")
    private String callerIp;

    /** 请求来源：web/app/mini（默认网页端） */
    private String source = "web";

}
