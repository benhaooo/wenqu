package com.wenqu.infra.messaging;

import com.wenqu.infra.messaging.api.EventQueue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 事件交换机、各模块的事件队列及其延迟重试队列。
 *
 * <p>重试队列 {@code <name>.retry-n} 没有消费者，消息在其中停留 TTL 后经死信交换回到原队列。
 */
@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    @Bean
    MessageConverter messageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    Declarables eventDeclarables(List<EventQueue> queues) {
        TopicExchange exchange = new TopicExchange(EventQueue.EXCHANGE);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(exchange);
        for (EventQueue eventQueue : queues) {
            Queue queue = QueueBuilder.durable(eventQueue.name()).build();
            declarables.add(queue);
            eventQueue
                    .routingKeys()
                    .forEach(key -> declarables.add(
                            BindingBuilder.bind(queue).to(exchange).with(key)));
            for (int retry = 1; retry <= EventQueue.RETRY_DELAYS.size(); retry++) {
                declarables.add(QueueBuilder.durable(eventQueue.retryQueue(retry))
                        .ttl((int) EventQueue.RETRY_DELAYS.get(retry - 1).toMillis())
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(eventQueue.name())
                        .build());
            }
            declarables.add(QueueBuilder.durable(eventQueue.failedQueue()).build());
        }
        return new Declarables(declarables);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RabbitTemplate rabbitTemplate,
            List<EventQueue> queues) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(0)
                .recoverer(new DelayedRetryRecoverer(rabbitTemplate, queues))
                .build());
        return factory;
    }
}
