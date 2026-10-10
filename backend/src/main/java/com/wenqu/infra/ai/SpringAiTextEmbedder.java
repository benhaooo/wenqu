package com.wenqu.infra.ai;

import com.wenqu.infra.ai.api.TextEmbedder;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
class SpringAiTextEmbedder implements TextEmbedder {

    private final EmbeddingModel embeddingModel;
    private final AiProperties.Embedding properties;

    SpringAiTextEmbedder(EmbeddingModel embeddingModel, AiProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties.embedding();
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }

    /**
     * @throws IllegalStateException 模型返回的向量维度与配置不一致（模型不支持 dimensions 参数或配置错误）
     */
    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = embeddingModel.embed(texts);
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException(
                    "Embedding model returned %d vectors for %d texts".formatted(vectors.size(), texts.size()));
        }
        for (float[] vector : vectors) {
            if (vector.length != properties.dimensions()) {
                throw new IllegalStateException(
                        "Embedding model %s returned %d dimensions, but AI_EMBEDDING_DIMENSIONS is %d"
                                .formatted(properties.model(), vector.length, properties.dimensions()));
            }
        }
        return vectors;
    }
}
