package com.wenqu.infra.outbox.service;

import com.wenqu.infra.outbox.api.OutboxPublisher;
import com.wenqu.infra.outbox.repository.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxService implements OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public OutboxService(OutboxRepository outboxRepository, JsonMapper jsonMapper, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String routingKey, Object payload) {
        outboxRepository.insert(routingKey, jsonMapper.writeValueAsString(payload), clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDebounced(String routingKey, Object payload, String dedupKey, Duration delay) {
        outboxRepository.upsertDebounced(
                routingKey,
                jsonMapper.writeValueAsString(payload),
                dedupKey,
                clock.instant().plus(delay));
    }
}
