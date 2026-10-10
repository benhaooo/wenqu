package com.wenqu.retrieval.domain;

/** 检索命中的文本块与余弦相似度。 */
public record ScoredChunk(
        long id, long documentId, String headingPath, String content, int blockFrom, int blockTo, double similarity) {}
