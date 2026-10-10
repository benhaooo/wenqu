package com.wenqu.infra.messaging.api;

import java.time.Duration;
import java.util.List;

/**
 * 订阅事件的队列，由业务模块声明为 Bean，infra 负责创建队列、绑定与重试队列。
 *
 * <p>消费失败后依次延迟 {@link #RETRY_DELAYS} 重新投递，消息头 {@link #RETRIES_HEADER} 记录已重试次数；重试用尽后转入
 * {@code <name>.failed} 队列保留，需要在用尽时记录业务状态的消费者通过 {@link #isLastAttempt(Integer)} 自行处理。
 *
 * @param routingKeys 订阅的路由键（发件箱事件的 routingKey）
 */
public record EventQueue(String name, List<String> routingKeys) {

    /** 发件箱事件投递到的 topic 交换机。 */
    public static final String EXCHANGE = "wenqu.events";

    public static final String RETRIES_HEADER = "x-wenqu-retries";

    public static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5));

    public static boolean isLastAttempt(Integer retries) {
        return retries != null && retries >= RETRY_DELAYS.size();
    }

    public String retryQueue(int retry) {
        return name + ".retry-" + retry;
    }

    public String failedQueue() {
        return name + ".failed";
    }
}
