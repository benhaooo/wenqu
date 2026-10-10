package com.wenqu.retrieval.api;

/**
 * 待写入的文本块。
 *
 * @param embeddingText 实际向量化的文本，在 content 前加上文档标题与标题路径，提高检索召回
 * @param blockFrom 对应正文顶层块范围的起点（含）
 * @param blockTo 对应正文顶层块范围的终点（含）
 */
public record ChunkDraft(
        String headingPath, String content, String embeddingText, int tokenCount, int blockFrom, int blockTo) {}
