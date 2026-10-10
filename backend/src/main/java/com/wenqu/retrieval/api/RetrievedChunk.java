package com.wenqu.retrieval.api;

/** @param similarity 余弦相似度，越接近 1 越相关 */
public record RetrievedChunk(
        long chunkId,
        long documentId,
        String headingPath,
        String content,
        int blockFrom,
        int blockTo,
        double similarity) {}
