package com.wenqu.ingestion.domain;

import java.time.Instant;

/**
 * 索引任务。导入任务带有原文件信息（fileName 等），其他任务这些字段为 null。
 *
 * @param parsedAt 原文件已解析并写入正文的时间，重试时不再重复解析
 * @param chunkCount 切块数量，开始向量化后才有值
 * @param attempts 已开始处理的次数
 * @param errorMessage 失败原因；排队状态下表示上次失败的原因（等待重试）
 */
public record IngestJob(
        long id,
        long workspaceId,
        long documentId,
        JobTrigger trigger,
        JobStatus status,
        String fileName,
        Long fileSize,
        String mediaType,
        String objectKey,
        Instant parsedAt,
        Integer chunkCount,
        int embeddedCount,
        int attempts,
        String errorMessage,
        long createdBy,
        Instant createdAt,
        Instant finishedAt) {}
