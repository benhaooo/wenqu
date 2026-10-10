package com.wenqu.infra.outbox.domain;

/** 待投递的事件，payload 为 JSON。 */
public record OutboxMessage(long id, String routingKey, String payload) {}
