package org.rail.common.mq.listener;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.rail.common.core.model.event.OperationLogEvent;
import org.rail.common.core.model.message.OperationLogMessage;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.SnowflakeIdGenerator;
import org.rail.common.mq.producer.OperationLogMqProducer;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 核心操作日志 事件监听器
 * 职责：接收事件 → 发送 MQ → 最终落库
 * 仅在 mq 模块中存在，core 包完全无感知
 */
@Component
@RequiredArgsConstructor
public class OperationLogEventListener {

    private final OperationLogMqProducer mqProducer;

    /**
     * 异步监听
     * 核心日志不能影响主业务
     */
    @Async
    @EventListener(OperationLogEvent.class)
    public void listenCoreOperationLog(OperationLogEvent event) {
        try {
            OperationLogMessage message = BeanUtil.copyProperties(event, OperationLogMessage.class);

            // 设置MQ专属字段（幂等ID + 发送时间）
            message.setMessageId(SnowflakeIdGenerator.nextId());
            message.setSendTime(LocalDateTime.now());

            // 投递 MQ（后续由消费者落库）
            mqProducer.sendLog(message);
        } catch (Exception e) {
            LogUtils.error("核心操作日志投递失败，事件:{}", event, e);
        }
    }
}