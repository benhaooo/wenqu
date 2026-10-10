package com.wenqu.chat.domain;

/** 提供给模型的一条资料，number 是提示词中的资料编号，也是回答中的角标序号。 */
public record RetrievedSource(
        int number,
        long documentId,
        String documentTitle,
        long chunkId,
        String headingPath,
        String content,
        int blockFrom,
        int blockTo,
        double similarity) {

    public Citation toCitation() {
        return new Citation(
                number, documentId, documentTitle, chunkId, headingPath, content, blockFrom, blockTo, similarity);
    }
}
