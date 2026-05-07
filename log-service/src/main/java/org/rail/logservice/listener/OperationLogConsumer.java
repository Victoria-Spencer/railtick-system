package org.rail.logservice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.event.OperationLogEvent;
import org.rail.logservice.config.RabbitMQConfig;
import org.rail.logservice.dataobject.SysOperationLog;
import org.rail.logservice.mapper.SysOperationLogMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志消费者
 * 功能：异步监听日志事件，将日志写入数据库
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogConsumer  {

    private final SysOperationLogMapper operationLogMapper;

    /**
     * 监听日志事件，消费日志数据并入库
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consumeLog(OperationLogEvent event) {
        try {
            log.info("【日志消费者】收到：{}", event.getOperation());

            SysOperationLog logEntity = SysOperationLog.builder()
                    .userId(event.getUserId())
                    .userName(event.getUserName())
                    .operation(event.getOperation())
                    .requestMethod(event.getRequestMethod())
                    .requestUrl(event.getRequestUrl())
                    .requestIp(event.getRequestIp())
                    .requestParam(event.getRequestParam())
                    .operateStatus(event.getOperateStatus())
                    .errorMsg(event.getErrorMsg())
                    .costTime(event.getCostTime())
                    .createTime(event.getCreateTime())
                    .build();

            operationLogMapper.insert(logEntity);

        } catch (Exception e) {
            log.error("【日志消费者】入库失败", e);
        }
    }
}