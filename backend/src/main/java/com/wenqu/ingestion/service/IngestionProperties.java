package com.wenqu.ingestion.service;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 切块参数。修改后新的索引任务按新参数切块，已有文档需要重新索引才会生效。
 *
 * @param chunkMaxTokens 每个文本块的 token 上限
 * @param chunkOverlapTokens 相邻文本块的重叠 token 数
 */
@Validated
@ConfigurationProperties("wenqu.ingestion")
public record IngestionProperties(
        @Min(100) int chunkMaxTokens, @Min(0) int chunkOverlapTokens) {}
