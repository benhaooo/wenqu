package com.wenqu.chat.domain;

/**
 * 回答引用的文本块，回答时保存快照。
 *
 * @param marker 回答正文中的角标序号 [marker]
 */
public record Citation(
        int marker,
        long documentId,
        String documentTitle,
        long chunkId,
        String headingPath,
        String snippet,
        int blockFrom,
        int blockTo,
        double similarity) {}
