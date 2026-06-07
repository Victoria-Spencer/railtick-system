package org.rail.orderservice.mq.consumer;

import org.rail.common.mq.exception.MqException;
import org.rail.orderservice.mq.config.OrderRabbitMQConfig;
import org.rail.orderservice.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class OrderDelayConsumer {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = OrderRabbitMQConfig.ORDER_DELAY_QUEUE)
    public void consume(String orderSn) {
        try {
            orderService.cancelOrder(orderSn);
        } catch (Exception e) {
            throw new MqException("订单超时消息消费失败", e);
        }
    }
}