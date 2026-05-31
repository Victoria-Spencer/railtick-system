package org.rail.orderservice.mq.producer;

import org.rail.orderservice.constant.OrderRedisConstants;
import org.rail.orderservice.mq.config.RabbitMQConfig;
import org.rail.orderservice.mq.message.PreOrderDelayMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 预订单延迟消息 生产者
 * 统一封装发送逻辑，业务层直接调用
 */
@Component
public class PreOrderDelayProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送预订单超时释放延迟消息
     */
    public void sendPreOrderDelayMsg(Long userId, Long trainId, String preOrderSn) {
        // 构建消息体
        PreOrderDelayMessage message = new PreOrderDelayMessage();
        message.setUserId(userId);
        message.setTrainId(trainId);
        message.setPreOrderSn(preOrderSn);

        long delayTime = OrderRedisConstants.PRE_ORDER_DELAY_TIME_MILLIS;;
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PRE_ORDER_DELAY_EXCHANGE,
                RabbitMQConfig.PRE_ORDER_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().getHeaders().put("x-delay", delayTime);
                    return msg;
                }
        );
    }
}