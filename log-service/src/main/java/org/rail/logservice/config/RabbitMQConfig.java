package org.rail.logservice.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "operation.log.exchange";
    public static final String QUEUE = "operation.log.queue";
    public static final String ROUTING_KEY = "operation.log.key";

    // 交换机
    @Bean
    public Exchange operationLogExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    // 队列
    @Bean
    public Queue operationLogQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    // 绑定
    @Bean
    public Binding binding() {
        return BindingBuilder.bind(operationLogQueue())
                .to(operationLogExchange())
                .with(ROUTING_KEY)
                .noargs();
    }
}