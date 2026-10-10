package com.wenqu.infra.outbox.service;

import com.wenqu.infra.messaging.api.EventQueue;
import com.wenqu.infra.outbox.domain.OutboxMessage;
import com.wenqu.infra.outbox.repository.OutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 把到期的发件箱事件投递到 RabbitMQ。
 *
 * <p>在持有行锁的事务中发送并等待 broker 确认，确认后才标记为已投递：发送后、提交前崩溃会导致重复投递，不会丢失，订阅方按幂等处理。
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final long CONFIRM_TIMEOUT_MILLIS = 10_000;
    private static final Duration RETENTION = Duration.ofDays(7);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    OutboxRelay(
            OutboxRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wenqu.outbox.poll-interval:PT1S}")
    void relay() {
        try {
            Integer sent;
            do {
                sent = transactionTemplate.execute(status -> relayBatch());
            } while (sent != null && sent == BATCH_SIZE);
        } catch (RuntimeException e) {
            log.warn("Outbox relay failed, will retry on next poll: {}", e.getMessage());
        }
    }

    private int relayBatch() {
        List<OutboxMessage> due = outboxRepository.lockDue(clock.instant(), BATCH_SIZE);
        if (due.isEmpty()) {
            return 0;
        }
        rabbitTemplate.invoke(operations -> {
            for (OutboxMessage message : due) {
                operations.send(EventQueue.EXCHANGE, message.routingKey(), toAmqp(message));
            }
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MILLIS);
            return null;
        });
        outboxRepository.markPublished(due.stream().map(OutboxMessage::id).toList(), clock.instant());
        return due.size();
    }

    @Scheduled(cron = "0 30 3 * * *")
    void purgePublished() {
        int deleted = outboxRepository.deletePublishedBefore(clock.instant().minus(RETENTION));
        log.info("Purged {} published outbox events", deleted);
    }

    private static Message toAmqp(OutboxMessage message) {
        return MessageBuilder.withBody(message.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setMessageId("outbox-" + message.id())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
