package com.wenqu.retrieval.service;

import com.wenqu.infra.ai.api.TextEmbedder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确认向量列的维度与配置一致。迁移脚本的维度来自首次建表时的配置，之后修改 AI_EMBEDDING_DIMENSIONS 不会改变已有的列。
 */
@Component
class EmbeddingColumnCheck implements ApplicationRunner {

    private final RetrievalService retrievalService;
    private final TextEmbedder embedder;

    EmbeddingColumnCheck(RetrievalService retrievalService, TextEmbedder embedder) {
        this.retrievalService = retrievalService;
        this.embedder = embedder;
    }

    @Override
    public void run(ApplicationArguments args) {
        int column = retrievalService.embeddingColumnDimensions();
        if (column != embedder.dimensions()) {
            throw new IllegalStateException(
                    "retrieval.chunk.embedding is vector(%d), but AI_EMBEDDING_DIMENSIONS is %d. Rebuild the database (make db-reset) after changing the dimensions."
                            .formatted(column, embedder.dimensions()));
        }
    }
}
