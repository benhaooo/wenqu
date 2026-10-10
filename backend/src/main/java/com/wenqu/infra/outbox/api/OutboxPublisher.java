package com.wenqu.infra.outbox.api;

import java.time.Duration;

/**
 * 事务性发件箱：事件与业务数据在同一个事务中写入，事务提交后由转发器投递到 RabbitMQ 的事件交换机。见 docs/_decisions/0006。
 *
 * <p>必须在调用方的数据库事务中调用。payload 序列化为 JSON，订阅方按「至少一次」处理。
 */
public interface OutboxPublisher {

    void publish(String routingKey, Object payload);

    /** 防抖：同一 dedupKey 尚未投递的事件合并为一条（保留最新 payload），投递时间推迟到 delay 之后。 */
    void publishDebounced(String routingKey, Object payload, String dedupKey, Duration delay);
}
