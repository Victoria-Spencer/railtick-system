package org.rail.ticketservice.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 车票服务 RabbitMQ 独立配置
 */
@Configuration
public class TicketRabbitMQConfig {

    /**
     * 座位占用记录
     */
    public static final String SEAT_OCCUPY_SYNC_EXCHANGE = "ticket.seat.occupy.sync.exchange";
    public static final String SEAT_OCCUPY_SYNC_QUEUE = "ticket.seat.occupy.sync.queue";
    public static final String SEAT_OCCUPY_SYNC_ROUTING_KEY = "ticket.seat.occupy.sync";

    /**
     * 座位占用同步交换机
     */
    @Bean
    public DirectExchange seatOccupySyncExchange() {
        return new DirectExchange(SEAT_OCCUPY_SYNC_EXCHANGE, true, false);
    }

    /**
     * 座位占用落库队列
     */
    @Bean
    public Queue seatOccupySyncQueue() {
        return QueueBuilder.durable(SEAT_OCCUPY_SYNC_QUEUE).build();
    }

    /**
     * 队列与交换机绑定
     */
    @Bean
    public Binding seatOccupySyncBinding() {
        return BindingBuilder.bind(seatOccupySyncQueue())
                .to(seatOccupySyncExchange())
                .with(SEAT_OCCUPY_SYNC_ROUTING_KEY);
    }
}