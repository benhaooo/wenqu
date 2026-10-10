package com.wenqu.ingestion.repository;

import static com.wenqu.jooq.ingestion.Tables.INGEST_JOB;
import static org.jooq.impl.DSL.any;
import static org.jooq.impl.DSL.selectCount;

import com.wenqu.ingestion.domain.ImportListItem;
import com.wenqu.ingestion.domain.IngestJob;
import com.wenqu.ingestion.domain.JobStatus;
import com.wenqu.ingestion.domain.JobTrigger;
import com.wenqu.jooq.ingestion.tables.records.IngestJobRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

@Repository
public class IngestJobRepository {

    private static final List<String> FINISHED = List.of(JobStatus.SUCCEEDED.name(), JobStatus.FAILED.name());

    private final DSLContext dsl;

    public IngestJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long insertImport(
            long workspaceId,
            long documentId,
            String fileName,
            long fileSize,
            String mediaType,
            String objectKey,
            long userId) {
        return dsl.insertInto(INGEST_JOB)
                .set(INGEST_JOB.WORKSPACE_ID, workspaceId)
                .set(INGEST_JOB.DOCUMENT_ID, documentId)
                .set(INGEST_JOB.TRIGGER, JobTrigger.IMPORT.name())
                .set(INGEST_JOB.STATUS, JobStatus.QUEUED.name())
                .set(INGEST_JOB.FILE_NAME, fileName)
                .set(INGEST_JOB.FILE_SIZE, fileSize)
                .set(INGEST_JOB.MEDIA_TYPE, mediaType)
                .set(INGEST_JOB.OBJECT_KEY, objectKey)
                .set(INGEST_JOB.CREATED_BY, userId)
                .returning(INGEST_JOB.ID)
                .fetchSingle(INGEST_JOB.ID);
    }

    public long insertIndexJob(long workspaceId, long documentId, JobTrigger trigger, long userId) {
        return dsl.insertInto(INGEST_JOB)
                .set(INGEST_JOB.WORKSPACE_ID, workspaceId)
                .set(INGEST_JOB.DOCUMENT_ID, documentId)
                .set(INGEST_JOB.TRIGGER, trigger.name())
                .set(INGEST_JOB.STATUS, JobStatus.QUEUED.name())
                .set(INGEST_JOB.CREATED_BY, userId)
                .returning(INGEST_JOB.ID)
                .fetchSingle(INGEST_JOB.ID);
    }

    /** 文档是否有尚未开始处理的任务（新任务会处理到最新正文，不必重复排队）。 */
    public boolean hasQueuedJob(long workspaceId, long documentId) {
        return dsl.fetchExists(
                INGEST_JOB,
                INGEST_JOB
                        .WORKSPACE_ID
                        .eq(workspaceId)
                        .and(INGEST_JOB.DOCUMENT_ID.eq(documentId))
                        .and(INGEST_JOB.STATUS.eq(JobStatus.QUEUED.name()))
                        .and(INGEST_JOB.ATTEMPTS.eq(0)));
    }

    public Optional<IngestJob> find(long id) {
        return dsl.selectFrom(INGEST_JOB).where(INGEST_JOB.ID.eq(id)).fetchOptional(IngestJobRepository::toJob);
    }

    public Optional<IngestJob> find(long workspaceId, long id) {
        return dsl.selectFrom(INGEST_JOB)
                .where(INGEST_JOB.ID.eq(id))
                .and(INGEST_JOB.WORKSPACE_ID.eq(workspaceId))
                .fetchOptional(IngestJobRepository::toJob);
    }

    /**
     * 开始一次处理：未结束的任务尝试次数加一。重复投递导致同一任务被并发处理时，各步骤都是幂等的，结果一致。
     *
     * @return 任务已结束（成功、失败或不存在）时为空
     */
    public Optional<IngestJob> start(long id, Instant now) {
        return dsl.update(INGEST_JOB)
                .set(INGEST_JOB.ATTEMPTS, INGEST_JOB.ATTEMPTS.plus(1))
                .set(INGEST_JOB.UPDATED_AT, now)
                .where(INGEST_JOB.ID.eq(id))
                .and(INGEST_JOB.STATUS.notIn(FINISHED))
                .returning()
                .fetchOptional(IngestJobRepository::toJob);
    }

    public void updateStatus(long id, JobStatus status, Instant now) {
        dsl.update(INGEST_JOB)
                .set(INGEST_JOB.STATUS, status.name())
                .set(INGEST_JOB.UPDATED_AT, now)
                .where(INGEST_JOB.ID.eq(id))
                .execute();
    }

    public void markParsed(long id, Instant now) {
        dsl.update(INGEST_JOB)
                .set(INGEST_JOB.PARSED_AT, now)
                .set(INGEST_JOB.UPDATED_AT, now)
                .where(INGEST_JOB.ID.eq(id))
                .execute();
    }

    public void startEmbedding(long id, int chunkCount, Instant now) {
        dsl.update(INGEST_JOB)
                .set(INGEST_JOB.STATUS, JobStatus.EMBEDDING.name())
                .set(INGEST_JOB.CHUNK_COUNT, chunkCount)
                .set(INGEST_JOB.EMBEDDED_COUNT, 0)
                .set(INGEST_JOB.UPDATED_AT, now)
                .where(INGEST_JOB.ID.eq(id))
                .execute();
    }

    public void updateProgress(long id, int embeddedCount, Instant now) {
        dsl.update(INGEST_JOB)
                .set(INGEST_JOB.EMBEDDED_COUNT, embeddedCount)
                .set(INGEST_JOB.UPDATED_AT, now)
                .where(INGEST_JOB.ID.eq(id))
                .execute();
    }

    public void finish(long id, JobStatus status, String errorMessage, Instant now) {
        dsl.update(INGEST_JOB)
                .set(INGEST_JOB.STATUS, status.name())
                .set(INGEST_JOB.ERROR_MESSAGE, errorMessage)
                .set(INGEST_JOB.UPDATED_AT, now)
                .set(INGEST_JOB.FINISHED_AT, now)
                .where(INGEST_JOB.ID.eq(id))
                .execute();
    }

    /** 本次处理失败、等待延迟重试。 */
    public void markWaitingRetry(long id, String errorMessage, Instant now) {
        dsl.update(INGEST_JOB)
                .set(INGEST_JOB.STATUS, JobStatus.QUEUED.name())
                .set(INGEST_JOB.ERROR_MESSAGE, errorMessage)
                .set(INGEST_JOB.UPDATED_AT, now)
                .where(INGEST_JOB.ID.eq(id))
                .execute();
    }

    /** 失败的任务重新排队，返回是否成功（只有失败的任务可以重试）。 */
    public boolean requeueFailed(long workspaceId, long id, Instant now) {
        return dsl.update(INGEST_JOB)
                        .set(INGEST_JOB.STATUS, JobStatus.QUEUED.name())
                        .set(INGEST_JOB.ATTEMPTS, 0)
                        .set(INGEST_JOB.ERROR_MESSAGE, (String) null)
                        .set(INGEST_JOB.FINISHED_AT, (Instant) null)
                        .set(INGEST_JOB.UPDATED_AT, now)
                        .where(INGEST_JOB.ID.eq(id))
                        .and(INGEST_JOB.WORKSPACE_ID.eq(workspaceId))
                        .and(INGEST_JOB.STATUS.eq(JobStatus.FAILED.name()))
                        .execute()
                > 0;
    }

    /** 删除文档在 beforeId 之前已结束的非导入任务，避免每次编辑都留下一条记录。 */
    public void deleteFinishedIndexJobsBefore(long workspaceId, long documentId, long beforeId) {
        dsl.deleteFrom(INGEST_JOB)
                .where(INGEST_JOB.WORKSPACE_ID.eq(workspaceId))
                .and(INGEST_JOB.DOCUMENT_ID.eq(documentId))
                .and(INGEST_JOB.ID.lt(beforeId))
                .and(INGEST_JOB.TRIGGER.ne(JobTrigger.IMPORT.name()))
                .and(INGEST_JOB.STATUS.in(FINISHED))
                .execute();
    }

    /** 空间内最近的导入任务及排队位置。 */
    public List<ImportListItem> findImports(long workspaceId, int limit) {
        var ahead = INGEST_JOB.as("ahead");
        Field<Integer> queuedAhead = selectCount()
                .from(ahead)
                .where(ahead.STATUS.eq(JobStatus.QUEUED.name()))
                .and(ahead.ATTEMPTS.eq(0))
                .and(ahead.ID.lt(INGEST_JOB.ID))
                .asField("queued_ahead");
        return dsl.select(INGEST_JOB.asterisk(), queuedAhead)
                .from(INGEST_JOB)
                .where(INGEST_JOB.WORKSPACE_ID.eq(workspaceId))
                .and(INGEST_JOB.TRIGGER.eq(JobTrigger.IMPORT.name()))
                .orderBy(INGEST_JOB.ID.desc())
                .limit(limit)
                .fetch(record -> {
                    IngestJob job = toJob(record.into(INGEST_JOB));
                    boolean waiting = job.status() == JobStatus.QUEUED && job.attempts() == 0;
                    return new ImportListItem(job, waiting ? record.get(queuedAhead) : 0);
                });
    }

    /** 空间内每篇文档最近一次任务中未成功的（处理中、等待重试或失败）。 */
    public List<IngestJob> findUnfinishedLatestByDocument(long workspaceId) {
        var latest = dsl.selectDistinct(INGEST_JOB.asterisk())
                .on(INGEST_JOB.DOCUMENT_ID)
                .from(INGEST_JOB)
                .where(INGEST_JOB.WORKSPACE_ID.eq(workspaceId))
                .orderBy(INGEST_JOB.DOCUMENT_ID, INGEST_JOB.ID.desc())
                .asTable("latest");
        return dsl.selectFrom(latest)
                .where(latest.field(INGEST_JOB.STATUS).ne(JobStatus.SUCCEEDED.name()))
                .fetch(record -> toJob(record.into(INGEST_JOB)));
    }

    /** 文档最近一次导入任务（下载原文件）。 */
    public Optional<IngestJob> findLatestImport(long workspaceId, long documentId) {
        return dsl.selectFrom(INGEST_JOB)
                .where(INGEST_JOB.WORKSPACE_ID.eq(workspaceId))
                .and(INGEST_JOB.DOCUMENT_ID.eq(documentId))
                .and(INGEST_JOB.TRIGGER.eq(JobTrigger.IMPORT.name()))
                .orderBy(INGEST_JOB.ID.desc())
                .limit(1)
                .fetchOptional(IngestJobRepository::toJob);
    }

    /** 删除文档的全部任务，返回导入原文件的对象键。 */
    public List<String> deleteByDocuments(Collection<Long> documentIds) {
        return dsl
                .deleteFrom(INGEST_JOB)
                .where(INGEST_JOB.DOCUMENT_ID.eq(any(documentIds.toArray(Long[]::new))))
                .returning(INGEST_JOB.OBJECT_KEY)
                .fetch(INGEST_JOB.OBJECT_KEY)
                .stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static IngestJob toJob(IngestJobRecord record) {
        return new IngestJob(
                record.getId(),
                record.getWorkspaceId(),
                record.getDocumentId(),
                JobTrigger.valueOf(record.getTrigger()),
                JobStatus.valueOf(record.getStatus()),
                record.getFileName(),
                record.getFileSize(),
                record.getMediaType(),
                record.getObjectKey(),
                record.getParsedAt(),
                record.getChunkCount(),
                record.getEmbeddedCount(),
                record.getAttempts(),
                record.getErrorMessage(),
                record.getCreatedBy(),
                record.getCreatedAt(),
                record.getFinishedAt());
    }
}
