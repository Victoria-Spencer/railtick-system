package org.rail.orderservice.mq.producer;

import org.rail.orderservice.model.entity.Order;
import org.rail.orderservice.model.entity.OrderDetails;
import org.rail.orderservice.mq.config.OrderRabbitMQConfig;
import org.rail.orderservice.mq.message.OrderCreateMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderCreateProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendOrderCreateMsg(Order order, List<OrderDetails> detailsList) {
        OrderCreateMessage message = new OrderCreateMessage();
        message.setOrder(order);
        message.setOrderDetailsList(detailsList);
        // 发送到订单创建队列
        rabbitTemplate.convertAndSend(OrderRabbitMQConfig.ORDER_EXCHANGE, OrderRabbitMQConfig.ORDER_CREATE_ROUTING_KEY, message);
    }
}