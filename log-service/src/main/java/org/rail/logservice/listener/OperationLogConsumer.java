package org.rail.logservice.listener;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.rail.common.core.model.message.OperationLogMessage;
import org.rail.common.core.util.LogUtils;
import org.rail.logservice.config.LogRabbitMQConfig;
import org.rail.logservice.entity.SysOperationLog;
import org.rail.logservice.mapper.SysOperationLogMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志消费者
 * 功能：异步监听日志事件，将日志写入数据库
 */
@Component
@RequiredArgsConstructor
public class OperationLogConsumer  {

    private final SysOperationLogMapper operationLogMapper;

    /**
     * 监听日志事件，消费日志数据并入库
     */
    @RabbitListener(queues = LogRabbitMQConfig.QUEUE)
    public void consumeLog(OperationLogMessage message) {
        long start = System.currentTimeMillis();
        StackTraceElement stackTrace = Thread.currentThread().getStackTrace()[1];
        String className = stackTrace.getClassName().substring(stackTrace.getClassName().lastIndexOf(".") + 1);
        String methodName = stackTrace.getMethodName();
        String action = className + "." + methodName;
        Object[] args = {message};

        try {
            SysOperationLog logEntity = BeanUtil.copyProperties(message, SysOperationLog.class);
            logEntity.setConsumeTime(LocalDateTime.now());

            operationLogMapper.insert(logEntity);

            LogUtils.monitor("log-service", action, start,
                    LogUtils.SUCCESS, args, null
            );
        } catch (DuplicateKeyException e) {
            // 重复消息：唯一索引冲突，直接忽略
        } catch (Exception e) {
            LogUtils.monitor("log-service", action, start,
                    LogUtils.FAIL, args, e
            );
            throw e;
        }
    }
}