package com.wenqu.ingestion.domain;

/**
 * 切块结果。
 *
 * @param headingPath 所在的标题路径，如「部署 > 回滚」，位于第一个标题之前时为空
 * @param blockFrom 对应正文顶层块范围的起点（含）
 * @param blockTo 对应正文顶层块范围的终点（含）
 */
public record TextChunk(String headingPath, String content, int tokenCount, int blockFrom, int blockTo) {}
