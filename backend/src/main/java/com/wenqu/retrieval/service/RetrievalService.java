package com.wenqu.retrieval.service;

import com.wenqu.infra.ai.api.TextEmbedder;
import com.wenqu.retrieval.api.ChunkDraft;
import com.wenqu.retrieval.api.RetrievalApi;
import com.wenqu.retrieval.api.RetrievedChunk;
import com.wenqu.retrieval.domain.Chunk;
import com.wenqu.retrieval.repository.ChunkRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetrievalService implements RetrievalApi {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    /** 单次 Embedding 请求的文本数，兼顾请求大小与进度更新频率。 */
    static final int EMBEDDING_BATCH_SIZE = 32;

    private final ChunkRepository chunkRepository;
    private final TextEmbedder embedder;
    private final TransactionTemplate transactionTemplate;

    public RetrievalService(
            ChunkRepository chunkRepository, TextEmbedder embedder, TransactionTemplate transactionTemplate) {
        this.chunkRepository = chunkRepository;
        this.embedder = embedder;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public OptionalInt indexedVersion(long documentId) {
        return chunkRepository
                .findIndexedVersion(documentId)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }

    /** 先完成全部向量化（耗时、可能失败），再在一个短事务中替换，失败时已有文本块不受影响。 */
    @Override
    public void replaceChunks(
            long workspaceId, long documentId, int version, List<ChunkDraft> drafts, IntConsumer onEmbedded) {
        List<float[]> vectors = new ArrayList<>(drafts.size());
        for (int start = 0; start < drafts.size(); start += EMBEDDING_BATCH_SIZE) {
            List<String> texts = drafts.subList(start, Math.min(drafts.size(), start + EMBEDDING_BATCH_SIZE)).stream()
                    .map(ChunkDraft::embeddingText)
                    .toList();
            vectors.addAll(embedder.embed(texts));
            onEmbedded.accept(vectors.size());
        }
        List<Chunk> chunks = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            chunks.add(new Chunk(
                    i,
                    draft.headingPath(),
                    draft.content(),
                    draft.tokenCount(),
                    draft.blockFrom(),
                    draft.blockTo(),
                    embedder.model(),
                    vectors.get(i)));
        }
        transactionTemplate.executeWithoutResult(status -> {
            chunkRepository.lockDocument(documentId);
            int indexed = chunkRepository.findIndexedVersion(documentId).orElse(-1);
            if (indexed > version) {
                log.info("Skip indexing document {} v{}, v{} already indexed", documentId, version, indexed);
                return;
            }
            chunkRepository.deleteByDocument(documentId);
            chunkRepository.insert(workspaceId, documentId, version, chunks);
        });
    }

    @Override
    public List<RetrievedChunk> search(long workspaceId, Collection<Long> documentIds, String query, int limit) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        float[] vector = embedder.embed(List.of(query)).getFirst();
        return transactionTemplate
                .execute(status ->
                        chunkRepository.searchNearest(workspaceId, documentIds, embedder.model(), vector, limit))
                .stream()
                .sorted(Comparator.comparingDouble(chunk -> -chunk.similarity()))
                .map(chunk -> new RetrievedChunk(
                        chunk.id(),
                        chunk.documentId(),
                        chunk.headingPath(),
                        chunk.content(),
                        chunk.blockFrom(),
                        chunk.blockTo(),
                        chunk.similarity()))
                .toList();
    }

    public int deleteByDocuments(Collection<Long> documentIds) {
        return chunkRepository.deleteByDocuments(documentIds);
    }

    public int embeddingColumnDimensions() {
        return chunkRepository.embeddingColumnDimensions();
    }
}
