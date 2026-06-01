package org.rail.orderservice.mq.producer;

import org.rail.orderservice.constant.OrderRedisConstants;
import org.rail.orderservice.mq.config.OrderRabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderDelayProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendOrderDelayMsg(String orderSn) {
        int delayTime = OrderRedisConstants.PRE_ORDER_DELAY_TIME_MILLIS;

        rabbitTemplate.convertAndSend(
                OrderRabbitMQConfig.ORDER_DELAY_EXCHANGE,
                OrderRabbitMQConfig.ORDER_DELAY_ROUTING_KEY,
                orderSn,
                msg -> {
                    msg.getMessageProperties().getHeaders().put("x-delay", delayTime);
                    return msg;
                }
        );
    }
}