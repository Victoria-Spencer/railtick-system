package org.rail.orderservice.mq.producer;

import org.rail.orderservice.constant.OrderRedisConstants;
import org.rail.orderservice.mq.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderDelayProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendOrderDelayMsg(String orderSn) {
        long delayTime = OrderRedisConstants.PRE_ORDER_DELAY_TIME_MILLIS;

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                orderSn,
                msg -> {
                    msg.getMessageProperties().getHeaders().put("x-delay", delayTime);
                    return msg;
                }
        );
    }
}