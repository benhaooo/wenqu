package com.wenqu.ingestion.service;

import com.wenqu.document.api.DocumentContentChangedEvent;
import com.wenqu.document.api.DocumentsPurgedEvent;
import com.wenqu.infra.messaging.api.EventQueue;
import com.wenqu.infra.storage.api.ObjectStorage;
import com.wenqu.ingestion.domain.IngestJobQueuedEvent;
import com.wenqu.ingestion.domain.JobTrigger;
import java.util.List;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;

/** ingestion 订阅的事件。消费失败按 {@link EventQueue#RETRY_DELAYS} 延迟重试。 */
@Configuration(proxyBeanMethods = false)
class IngestionListeners {

    static final String JOB_QUEUE = "ingestion.ingest-jobs";
    static final String CONTENT_CHANGED_QUEUE = "ingestion.document-content-changed";
    static final String PURGED_QUEUE = "ingestion.documents-purged";

    private final IngestJobProcessor processor;
    private final IngestionService ingestionService;
    private final ObjectStorage objectStorage;

    IngestionListeners(IngestJobProcessor processor, IngestionService ingestionService, ObjectStorage objectStorage) {
        this.processor = processor;
        this.ingestionService = ingestionService;
        this.objectStorage = objectStorage;
    }

    @Bean
    static EventQueue ingestionJobQueue() {
        return new EventQueue(JOB_QUEUE, List.of(IngestJobQueuedEvent.ROUTING_KEY));
    }

    @Bean
    static EventQueue ingestionContentChangedQueue() {
        return new EventQueue(CONTENT_CHANGED_QUEUE, List.of(DocumentContentChangedEvent.ROUTING_KEY));
    }

    @Bean
    static EventQueue ingestionDocumentsPurgedQueue() {
        return new EventQueue(PURGED_QUEUE, List.of(DocumentsPurgedEvent.ROUTING_KEY));
    }

    /** 任务处理调用模型，耗时较长，两个消费者并行。 */
    @RabbitListener(queues = JOB_QUEUE, concurrency = "2")
    void onJobQueued(
            IngestJobQueuedEvent event, @Header(name = EventQueue.RETRIES_HEADER, required = false) Integer retries) {
        processor.process(event.jobId(), EventQueue.isLastAttempt(retries));
    }

    @RabbitListener(queues = CONTENT_CHANGED_QUEUE)
    void onContentChanged(DocumentContentChangedEvent event) {
        ingestionService.enqueueIndex(event.workspaceId(), event.documentId(), JobTrigger.EDIT, event.userId());
    }

    /** 先在事务中删除任务记录，提交后再删除原文件：对象删除失败只会留下无人引用的对象。 */
    @RabbitListener(queues = PURGED_QUEUE)
    void onPurged(DocumentsPurgedEvent event) {
        objectStorage.delete(ingestionService.deleteJobsOfDocuments(event.documentIds()));
    }
}
