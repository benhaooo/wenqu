package com.wenqu.infra.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型客户端，通过 OpenAI 兼容接口接入，见 docs/_decisions/0004。业务模块只依赖 Spring AI 的 {@link ChatModel} 和
 * {@link com.wenqu.infra.ai.api.TextEmbedder}。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
class AiConfiguration {

    /** 模型请求失败由调用方处理（索引任务有延迟重试队列，问答直接提示用户），客户端只重试一次网络抖动。 */
    private static final int MAX_RETRIES = 1;

    @Bean
    ChatModel chatModel(AiProperties properties) {
        AiProperties.Chat chat = properties.chat();
        return OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(chat.baseUrl())
                        .apiKey(chat.apiKey())
                        .model(chat.model())
                        .temperature(chat.temperature())
                        .timeout(chat.timeout())
                        .maxRetries(MAX_RETRIES)
                        .build())
                .build();
    }

    @Bean
    EmbeddingModel embeddingModel(AiProperties properties) {
        AiProperties.Embedding embedding = properties.embedding();
        return new OpenAiEmbeddingModel(OpenAiEmbeddingOptions.builder()
                .baseUrl(embedding.baseUrl())
                .apiKey(embedding.apiKey())
                .model(embedding.model())
                .dimensions(embedding.dimensions())
                .timeout(embedding.timeout())
                .maxRetries(MAX_RETRIES)
                .build());
    }
}
