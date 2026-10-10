package com.wenqu.chat.service;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param topK 每次检索取多少个文本块
 * @param minSimilarity 低于该余弦相似度的文本块视为不相关；全部低于时不调用模型，直接回复没有找到
 * @param historyRounds 携带最近几轮问答作为上下文
 */
@Validated
@ConfigurationProperties("wenqu.chat")
public record ChatProperties(
        @Min(1) @Max(20) int topK,
        @DecimalMin("0") @DecimalMax("1") double minSimilarity,
        @Min(0) @Max(20) int historyRounds) {}
