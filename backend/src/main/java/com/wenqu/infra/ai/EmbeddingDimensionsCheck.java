package com.wenqu.infra.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时试算一条文本，确认 Embedding 模型返回的维度与配置一致，维度不一致时启动失败，避免写入后才发现。
 *
 * <p>模型服务暂时不可用时只记录警告，不阻止启动：索引任务会延迟重试，问答会提示用户。
 */
@Component
class EmbeddingDimensionsCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDimensionsCheck.class);

    private final EmbeddingModel embeddingModel;
    private final AiProperties.Embedding properties;

    EmbeddingDimensionsCheck(EmbeddingModel embeddingModel, AiProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties.embedding();
    }

    @Override
    public void run(ApplicationArguments args) {
        int actual;
        try {
            actual = embeddingModel.embed("问渠").length;
        } catch (RuntimeException e) {
            log.warn("Embedding model {} is unreachable at startup: {}", properties.model(), e.getMessage());
            return;
        }
        if (actual != properties.dimensions()) {
            throw new IllegalStateException(
                    "Embedding model %s returns %d dimensions, but AI_EMBEDDING_DIMENSIONS is %d. Fix the configuration, or change the dimensions and rebuild the database (make db-reset)."
                            .formatted(properties.model(), actual, properties.dimensions()));
        }
        log.info("Embedding model {} returns {} dimensions", properties.model(), actual);
    }
}
