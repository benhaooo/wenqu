package com.wenqu.retrieval.service;

import com.wenqu.document.api.DocumentsPurgedEvent;
import com.wenqu.infra.messaging.api.EventQueue;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 文档彻底删除后删除其文本块。重复投递时删除 0 行，天然幂等。 */
@Configuration(proxyBeanMethods = false)
class DocumentsPurgedListener {

    static final String QUEUE = "retrieval.documents-purged";

    private static final Logger log = LoggerFactory.getLogger(DocumentsPurgedListener.class);

    private final RetrievalService retrievalService;

    DocumentsPurgedListener(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Bean
    static EventQueue retrievalDocumentsPurgedQueue() {
        return new EventQueue(QUEUE, List.of(DocumentsPurgedEvent.ROUTING_KEY));
    }

    @RabbitListener(queues = QUEUE)
    void onPurged(DocumentsPurgedEvent event) {
        int deleted = retrievalService.deleteByDocuments(event.documentIds());
        log.info(
                "Deleted {} chunks of {} purged documents",
                deleted,
                event.documentIds().size());
    }
}
