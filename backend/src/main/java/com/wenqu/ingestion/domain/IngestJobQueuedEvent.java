package com.wenqu.ingestion.domain;

/** 新的索引任务进入队列。由 ingestion 自己发布和消费，经发件箱投递保证任务一定会被处理。 */
public record IngestJobQueuedEvent(long jobId) {

    public static final String ROUTING_KEY = "ingestion.job-queued";
}
