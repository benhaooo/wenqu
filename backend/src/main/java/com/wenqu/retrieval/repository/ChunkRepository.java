package com.wenqu.retrieval.repository;

import static com.wenqu.jooq.retrieval.Tables.CHUNK;
import static org.jooq.impl.DSL.any;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.val;

import com.wenqu.retrieval.domain.Chunk;
import com.wenqu.retrieval.domain.ScoredChunk;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {

    private final DSLContext dsl;

    public ChunkRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 串行化同一文档的索引写入（事务结束时释放）。键由字符串哈希得到，与按空间 id 加的锁不在同一取值空间。 */
    public void lockDocument(long documentId) {
        dsl.execute("select pg_advisory_xact_lock(hashtextextended('retrieval.chunk:' || ?, 0))", documentId);
    }

    public Optional<Integer> findIndexedVersion(long documentId) {
        return dsl.select(max(CHUNK.DOCUMENT_VERSION))
                .from(CHUNK)
                .where(CHUNK.DOCUMENT_ID.eq(documentId))
                .fetchOptional(Record1::value1);
    }

    public void deleteByDocument(long documentId) {
        dsl.deleteFrom(CHUNK).where(CHUNK.DOCUMENT_ID.eq(documentId)).execute();
    }

    public int deleteByDocuments(Collection<Long> documentIds) {
        return dsl.deleteFrom(CHUNK)
                .where(CHUNK.DOCUMENT_ID.eq(any(documentIds.toArray(Long[]::new))))
                .execute();
    }

    public void insert(long workspaceId, long documentId, int version, List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        var insert = dsl.insertInto(
                CHUNK,
                CHUNK.WORKSPACE_ID,
                CHUNK.DOCUMENT_ID,
                CHUNK.DOCUMENT_VERSION,
                CHUNK.CHUNK_INDEX,
                CHUNK.HEADING_PATH,
                CHUNK.CONTENT,
                CHUNK.TOKEN_COUNT,
                CHUNK.BLOCK_FROM,
                CHUNK.BLOCK_TO,
                CHUNK.EMBEDDING_MODEL,
                CHUNK.EMBEDDING);
        for (Chunk chunk : chunks) {
            insert = insert.values(
                    workspaceId,
                    documentId,
                    version,
                    chunk.index(),
                    chunk.headingPath(),
                    chunk.content(),
                    chunk.tokenCount(),
                    chunk.blockFrom(),
                    chunk.blockTo(),
                    chunk.embeddingModel(),
                    chunk.embedding());
        }
        insert.execute();
    }

    /**
     * 余弦距离最近的文本块。开启 pgvector 迭代扫描：HNSW 先取近邻再过滤，范围过滤严格时不开启会返回不足 limit 条，见
     * docs/_decisions/0005。relaxed_order 下结果可能略微乱序，由调用方重新排序。必须在事务中调用（set local）。
     */
    public List<ScoredChunk> searchNearest(
            long workspaceId, Collection<Long> documentIds, String embeddingModel, float[] query, int limit) {
        dsl.execute("set local hnsw.iterative_scan = relaxed_order");
        Field<Double> distance =
                field("({0} <=> {1})", Double.class, CHUNK.EMBEDDING, val(query, CHUNK.EMBEDDING.getDataType()));
        Field<Double> similarity = inline(1.0).minus(distance).as("similarity");
        return dsl.select(
                        CHUNK.ID,
                        CHUNK.DOCUMENT_ID,
                        CHUNK.HEADING_PATH,
                        CHUNK.CONTENT,
                        CHUNK.BLOCK_FROM,
                        CHUNK.BLOCK_TO,
                        similarity)
                .from(CHUNK)
                .where(CHUNK.WORKSPACE_ID.eq(workspaceId))
                .and(CHUNK.DOCUMENT_ID.eq(any(documentIds.toArray(Long[]::new))))
                .and(CHUNK.EMBEDDING_MODEL.eq(embeddingModel))
                .orderBy(distance)
                .limit(limit)
                .fetch(record -> new ScoredChunk(
                        record.get(CHUNK.ID),
                        record.get(CHUNK.DOCUMENT_ID),
                        record.get(CHUNK.HEADING_PATH),
                        record.get(CHUNK.CONTENT),
                        record.get(CHUNK.BLOCK_FROM),
                        record.get(CHUNK.BLOCK_TO),
                        record.get(similarity)));
    }

    /** embedding 列声明的维度（vector 类型的 atttypmod 即维度）。 */
    public int embeddingColumnDimensions() {
        return dsl.resultQuery(
                        "select atttypmod from pg_attribute where attrelid = 'retrieval.chunk'::regclass and attname = 'embedding'")
                .fetchSingle(0, Integer.class);
    }
}
