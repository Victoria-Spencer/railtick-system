package org.rail.common.mq.producer;

import lombok.RequiredArgsConstructor;
import org.rail.common.core.model.message.OperationLogMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OperationLogMqProducer {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "operation.log.exchange";
    private static final String ROUTING_KEY = "operation.log.key";

    public void sendLog(OperationLogMessage message) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
    }
}