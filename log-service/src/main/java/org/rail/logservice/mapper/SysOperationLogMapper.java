package org.rail.logservice.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.rail.logservice.entity.SysOperationLog;

/**
 * 操作日志
 */
@Mapper
public interface SysOperationLogMapper {

    /**
     * 插入操作日志（纯注解SQL）
     * @param log 日志实体
     * @return 影响行数
     */
    @Insert("INSERT INTO sys_operation_log (" +
            "user_id, user_name, operation, request_method, request_url, " +
            "request_ip, request_param, operate_status, error_msg, cost_time, create_time, " +
            "message_id, send_time, consume_time" +
            ") VALUES (" +
            "#{userId}, #{username}, #{operation}, #{requestMethod}, #{requestUrl}, " +
            "#{requestIp}, #{requestParam}, #{operateStatus}, #{errorMsg}, #{costTime}, #{createTime}, " +
            "#{messageId}, #{sendTime}, #{consumeTime}" +
            ")"
    )
    int insert(SysOperationLog log);
}