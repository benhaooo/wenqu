package com.wenqu.ingestion.service;

import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.document.api.DocumentApi;
import com.wenqu.infra.outbox.api.OutboxPublisher;
import com.wenqu.infra.storage.api.ObjectStorage;
import com.wenqu.ingestion.domain.ImportFormat;
import com.wenqu.ingestion.domain.ImportListItem;
import com.wenqu.ingestion.domain.IngestJob;
import com.wenqu.ingestion.domain.IngestJobQueuedEvent;
import com.wenqu.ingestion.domain.JobTrigger;
import com.wenqu.ingestion.repository.IngestJobRepository;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 导入文件、索引任务排队与查询。调用方已通过 {@code @RequireWorkspaceRole} 完成角色校验。 */
@Service
public class IngestionService {

    static final int IMPORT_LIST_LIMIT = 50;
    static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final int MAX_TITLE_LENGTH = 200;

    private final IngestJobRepository jobRepository;
    private final DocumentApi documentApi;
    private final ObjectStorage objectStorage;
    private final OutboxPublisher outboxPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Tika tika = new Tika();

    public IngestionService(
            IngestJobRepository jobRepository,
            DocumentApi documentApi,
            ObjectStorage objectStorage,
            OutboxPublisher outboxPublisher,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.documentApi = documentApi;
        this.objectStorage = objectStorage;
        this.outboxPublisher = outboxPublisher;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * 原文件存入对象存储后，在一个事务中新建文档、创建导入任务并写入发件箱事件，解析在后台进行。
     *
     * <p>类型按文件内容检测（Markdown 与纯文本结合扩展名区分）。事务失败时删除已上传的原文件。
     */
    public IngestJob importFile(long workspaceId, Long parentId, MultipartFile file, long userId) throws IOException {
        if (file.getSize() > ImportFormat.MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件不能超过 20MB");
        }
        String fileName = fileName(file.getOriginalFilename());
        String mediaType;
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            mediaType = tika.detect(input, fileName);
        }
        ImportFormat format = ImportFormat.ofMediaType(mediaType)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_IMPORT_TYPE));
        String key = "workspaces/%d/imports/%s.%s".formatted(workspaceId, UUID.randomUUID(), format.extension());
        try (InputStream input = file.getInputStream()) {
            objectStorage.put(key, input, file.getSize(), mediaType);
        }
        try {
            return transactionTemplate.execute(status -> {
                long documentId = documentApi.createDocument(workspaceId, parentId, title(fileName), userId);
                long jobId = jobRepository.insertImport(
                        workspaceId, documentId, fileName, file.getSize(), mediaType, key, userId);
                outboxPublisher.publish(IngestJobQueuedEvent.ROUTING_KEY, new IngestJobQueuedEvent(jobId));
                return jobRepository.find(jobId).orElseThrow();
            });
        } catch (RuntimeException e) {
            objectStorage.delete(List.of(key));
            throw e;
        }
    }

    public List<ImportListItem> listImports(long workspaceId) {
        return jobRepository.findImports(workspaceId, IMPORT_LIST_LIMIT);
    }

    /** 每篇文档最近一次未成功的任务：处理中、等待重试或失败。 */
    public List<IngestJob> unfinishedJobs(long workspaceId) {
        return jobRepository.findUnfinishedLatestByDocument(workspaceId);
    }

    @Transactional
    public void retry(long workspaceId, long jobId) {
        jobRepository.find(workspaceId, jobId).orElseThrow(() -> new BusinessException(ErrorCode.INGEST_JOB_NOT_FOUND));
        if (!jobRepository.requeueFailed(workspaceId, jobId, clock.instant())) {
            throw new BusinessException(ErrorCode.INGEST_JOB_NOT_RETRYABLE);
        }
        outboxPublisher.publish(IngestJobQueuedEvent.ROUTING_KEY, new IngestJobQueuedEvent(jobId));
    }

    /** 文档没有排队中的任务时新建一个；已有排队任务时它会处理到最新正文，不重复排队。返回是否新建。 */
    @Transactional
    public boolean enqueueIndex(long workspaceId, long documentId, JobTrigger trigger, long userId) {
        if (jobRepository.hasQueuedJob(workspaceId, documentId)) {
            return false;
        }
        long jobId = jobRepository.insertIndexJob(workspaceId, documentId, trigger, userId);
        outboxPublisher.publish(IngestJobQueuedEvent.ROUTING_KEY, new IngestJobQueuedEvent(jobId));
        return true;
    }

    /** 空间内全部文档重新索引（更换 Embedding 模型或切块参数后），返回新排队的任务数。 */
    @Transactional
    public int reindexWorkspace(long workspaceId, long userId) {
        int queued = 0;
        for (long documentId : documentApi.findActiveDocumentIds(workspaceId, null)) {
            if (enqueueIndex(workspaceId, documentId, JobTrigger.REINDEX, userId)) {
                queued++;
            }
        }
        return queued;
    }

    public SourceFile sourceFile(long workspaceId, long documentId) {
        IngestJob job = jobRepository
                .findLatestImport(workspaceId, documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOURCE_FILE_NOT_FOUND));
        return new SourceFile(
                job.fileName(), objectStorage.signedDownloadUrl(job.objectKey(), DOWNLOAD_URL_TTL, job.fileName()));
    }

    /** 删除已彻底删除文档的任务记录，返回需要从对象存储删除的原文件。 */
    @Transactional
    public List<String> deleteJobsOfDocuments(Collection<Long> documentIds) {
        return jobRepository.deleteByDocuments(documentIds);
    }

    public record SourceFile(String fileName, URI url) {}

    /** 去掉客户端可能附带的路径，限制长度。 */
    static String fileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isEmpty()) {
            name = "未命名文件";
        }
        return name.length() > MAX_TITLE_LENGTH ? name.substring(name.length() - MAX_TITLE_LENGTH) : name;
    }

    /** 文档标题为去掉扩展名的文件名。 */
    static String title(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String title = dot > 0 ? fileName.substring(0, dot).strip() : fileName;
        return title.isEmpty() ? fileName : title;
    }
}
