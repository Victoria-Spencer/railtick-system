package org.rail.orderservice.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 延迟队列配置
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 预订单延迟队列
     */
    public static final String PRE_ORDER_DELAY_EXCHANGE = "pre.order.delay.exchange";
    public static final String PRE_ORDER_QUEUE = "pre.order.delay.queue";
    public static final String PRE_ORDER_ROUTING_KEY = "pre.order.delay";

    /**
     * 订单创建业务队列
     */
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_CREATE_QUEUE = "order.create.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";

    /**
     * 订单超时延迟队列
     */
    public static final String ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";

    @Bean
    public CustomExchange preOrderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        // 类型固定：x-delayed-message
        return new CustomExchange(
                PRE_ORDER_DELAY_EXCHANGE,
                "x-delayed-message",
                true,
                false,
                args
        );
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(ORDER_DELAY_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue preOrderQueue() {
        return QueueBuilder.durable(PRE_ORDER_QUEUE).build();
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(ORDER_CREATE_QUEUE).build();
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE).build();
    }

    @Bean
    public Binding preOrderBinding() {
        return BindingBuilder.bind(preOrderQueue())
                .to(preOrderDelayExchange())
                .with(PRE_ORDER_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue())
                .to(orderExchange())
                .with(ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(ORDER_DELAY_ROUTING_KEY)
                .noargs();
    }
}