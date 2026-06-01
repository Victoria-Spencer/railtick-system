package org.rail.common.mq.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * RabbitMQ 统一配置类
 *  生产者自动传上下文 + 消费者自动取上下文
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 生产者RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter,
            MessagePostProcessor requestContextMessagePostProcessor) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        rabbitTemplate.setBeforePublishPostProcessors(requestContextMessagePostProcessor);
        return rabbitTemplate;
    }

    /**
     * 全局消息后置处理器（自动把上下文放入消息头）
     * 所有消息发送前，自动执行
     */
    @Bean
    public MessagePostProcessor requestContextMessagePostProcessor() {
        return message -> {
            RequestContext context = RequestContextHolder.getRequestContext();
            if (Objects.nonNull(context)) {
                // 全字段存入消息头
                message.getMessageProperties().setHeader("START_TIME", context.getStartTime());
                message.getMessageProperties().setHeader("REQUEST_ID", context.getRequestId());
                message.getMessageProperties().setHeader("ACCOUNT_ID", context.getAccountId());
                message.getMessageProperties().setHeader("USERNAME", context.getUsername());
                message.getMessageProperties().setHeader("CALLER_IP", context.getCallerIp());
                message.getMessageProperties().setHeader("SOURCE", context.getSource());
            }
            return message;
        };
    }

    /**
     * MQ消费者监听器工厂（使用Spring默认线程池）
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter,
            MethodInterceptor consumerContextAdvice) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        // 绑定JSON转换器
        factory.setMessageConverter(rabbitMessageConverter);
        // 绑定全局上下文拦截器，自动处理上下文
        factory.setAdviceChain(consumerContextAdvice);
        return factory;
    }

    /**
     * 消费者上下文环绕通知
     * 1. 消费前：自动从消息头取上下文 → 设置到线程
     * 2. 消费中：执行业务代码
     * 3. 消费后：自动清空上下文，防止线程池污染
     */
    @Bean
    public ConsumerContextAdvice consumerContextAdvice() {
        return new ConsumerContextAdvice();
    }

    public static class ConsumerContextAdvice implements MethodInterceptor {
        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            RequestContext context = new RequestContext();
            // 遍历方法参数，找到 RabbitMQ 原生 Message 对象
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Message nativeMessage) {
                    context.setStartTime(nativeMessage.getMessageProperties().getHeader("START_TIME"));
                    context.setRequestId(nativeMessage.getMessageProperties().getHeader("REQUEST_ID"));
                    context.setAccountId(nativeMessage.getMessageProperties().getHeader("ACCOUNT_ID"));
                    context.setUsername(nativeMessage.getMessageProperties().getHeader("USERNAME"));
                    context.setCallerIp(nativeMessage.getMessageProperties().getHeader("CALLER_IP"));
                    context.setSource(nativeMessage.getMessageProperties().getHeader("SOURCE"));
                    break;
                }
            }

            try {
                RequestContextHolder.setRequestContext(context);
                return invocation.proceed();
            } finally {
                RequestContextHolder.clearRequestContext();
            }
        }
    }
}