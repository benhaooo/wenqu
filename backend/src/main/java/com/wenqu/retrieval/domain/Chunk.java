package com.wenqu.retrieval.domain;

/** 已向量化、待写入的文本块。 */
public record Chunk(
        int index,
        String headingPath,
        String content,
        int tokenCount,
        int blockFrom,
        int blockTo,
        String embeddingModel,
        float[] embedding) {}
