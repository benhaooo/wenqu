package com.wenqu.retrieval.api;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntConsumer;

/** 文本块的向量化写入与检索。 */
public interface RetrievalApi {

    /** 文档当前索引对应的正文版本；没有文本块时为空。 */
    OptionalInt indexedVersion(long documentId);

    /**
     * 向量化 drafts 并整体替换文档的文本块。比已有索引更旧的版本不会覆盖已有索引。
     *
     * @param onEmbedded 每完成一批向量化后回调已完成的数量，用于展示进度
     * @throws RuntimeException 模型调用失败，已有文本块保持不变
     */
    void replaceChunks(long workspaceId, long documentId, int version, List<ChunkDraft> drafts, IntConsumer onEmbedded);

    /**
     * 在 documentIds 范围内检索与 query 最相关的文本块，按相似度从高到低排列。
     *
     * @param documentIds 调用方确认过的未删除文档；为空时返回空列表
     */
    List<RetrievedChunk> search(long workspaceId, Collection<Long> documentIds, String query, int limit);
}
