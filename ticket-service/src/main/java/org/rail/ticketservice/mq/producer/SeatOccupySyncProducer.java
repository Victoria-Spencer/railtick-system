package org.rail.ticketservice.mq.producer;

import org.rail.ticketservice.mq.config.TicketRabbitMQConfig;
import org.rail.ticketservice.mq.message.SeatOccupySyncMessage;
import org.rail.ticketservice.model.entity.SeatIntervalOccupy;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 座位占用记录 消息生产者
 * 异步发送落库消息
 */
@Component
public class SeatOccupySyncProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送座位占用记录异步落库消息
     * @param occupyList 座位占用记录列表
     */
    public void sendSeatOccupySyncMsg(List<SeatIntervalOccupy> occupyList) {
        SeatOccupySyncMessage message = new SeatOccupySyncMessage();
        message.setOccupyList(occupyList);

        // 发送到车票服务专属队列
        rabbitTemplate.convertAndSend(
                TicketRabbitMQConfig.SEAT_OCCUPY_SYNC_EXCHANGE,
                TicketRabbitMQConfig.SEAT_OCCUPY_SYNC_ROUTING_KEY,
                message
        );
    }
}