package org.rail.orderservice.mq.consumer;

import org.rail.common.core.exception.MqException;
import org.rail.orderservice.mapper.OrderMapper;
import org.rail.orderservice.model.entity.Order;
import org.rail.orderservice.model.entity.OrderDetails;
import org.rail.orderservice.mq.config.OrderRabbitMQConfig;
import org.rail.orderservice.mq.message.OrderCreateMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OrderCreateConsumer {

    @Autowired
    private OrderMapper orderMapper;

    @Transactional
    @RabbitListener(queues = OrderRabbitMQConfig.ORDER_CREATE_QUEUE)
    public void consume(OrderCreateMessage message) {
        Order order = message.getOrder();
        List<OrderDetails> detailsList = message.getOrderDetailsList();

        try {
            // 异步落库：插入订单+明细
            orderMapper.insertOrder(order);
            orderMapper.batchInsertOrderDetails(detailsList);
        } catch (Exception e) {
            throw new MqException("订单创建落库失败：", e);
        }
    }
}