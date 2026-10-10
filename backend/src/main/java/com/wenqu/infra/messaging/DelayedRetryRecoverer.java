package com.wenqu.infra.messaging;

import com.wenqu.infra.messaging.api.EventQueue;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

/**
 * 消费失败的消息转入下一级延迟重试队列；重试用尽后转入 failed 队列。
 *
 * <p>等待 broker 确认转发成功后原消息才被确认；转发失败时抛出异常，原消息由 broker 重新投递，不会丢失。
 */
class DelayedRetryRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DelayedRetryRecoverer.class);

    private static final long CONFIRM_TIMEOUT_MILLIS = 10_000;

    private final RabbitTemplate rabbitTemplate;
    private final Map<String, EventQueue> queues;

    DelayedRetryRecoverer(RabbitTemplate rabbitTemplate, List<EventQueue> queues) {
        this.rabbitTemplate = rabbitTemplate;
        this.queues = queues.stream().collect(Collectors.toMap(EventQueue::name, Function.identity()));
    }

    @Override
    public void recover(Message message, Throwable cause) {
        MessageProperties properties = message.getMessageProperties();
        EventQueue queue = queues.get(properties.getConsumerQueue());
        if (queue == null) {
            throw new AmqpRejectAndDontRequeueException("Unknown queue " + properties.getConsumerQueue(), cause);
        }
        Throwable root = cause.getCause() == null ? cause : cause.getCause();
        Integer retries = properties.getHeader(EventQueue.RETRIES_HEADER);
        int done = retries == null ? 0 : retries;
        if (done < EventQueue.RETRY_DELAYS.size()) {
            properties.setHeader(EventQueue.RETRIES_HEADER, done + 1);
            sendConfirmed(queue.retryQueue(done + 1), message);
            log.warn(
                    "Message {} on {} failed, retry {} in {}: {}",
                    properties.getMessageId(),
                    queue.name(),
                    done + 1,
                    EventQueue.RETRY_DELAYS.get(done),
                    root.toString());
        } else {
            properties.setHeader("x-exception-message", String.valueOf(root.getMessage()));
            sendConfirmed(queue.failedQueue(), message);
            log.error("Message {} on {} failed after {} retries", properties.getMessageId(), queue.name(), done, root);
        }
    }

    private void sendConfirmed(String queue, Message message) {
        rabbitTemplate.invoke(operations -> {
            operations.send("", queue, message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MILLIS);
            return null;
        });
    }
}
