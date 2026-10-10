package com.wenqu.infra.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 对话模型与 Embedding 模型分别配置，可以来自不同的 OpenAI 兼容服务。 */
@Validated
@ConfigurationProperties("wenqu.ai")
public record AiProperties(
        @Valid @NotNull Chat chat, @Valid @NotNull Embedding embedding) {

    public record Chat(
            @NotBlank String baseUrl,
            @NotBlank String apiKey,
            @NotBlank String model,
            @NotNull Double temperature,
            @NotNull Duration timeout) {}

    /** dimensions 必须与 retrieval.chunk.embedding 列的维度一致（迁移脚本占位符 embedding_dimensions）。 */
    public record Embedding(
            @NotBlank String baseUrl,
            @NotBlank String apiKey,
            @NotBlank String model,
            @Min(1) @Max(2000) int dimensions,
            @NotNull Duration timeout) {}
}
