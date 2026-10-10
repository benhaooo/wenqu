package com.wenqu.ingestion.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.document.api.DocumentApi;
import com.wenqu.document.api.DocumentSnapshot;
import com.wenqu.document.api.ImportedBlock;
import com.wenqu.infra.storage.api.ObjectStorage;
import com.wenqu.ingestion.domain.Chunker;
import com.wenqu.ingestion.domain.ImportFormat;
import com.wenqu.ingestion.domain.IngestFailure;
import com.wenqu.ingestion.domain.IngestJob;
import com.wenqu.ingestion.domain.JobStatus;
import com.wenqu.ingestion.domain.JobTrigger;
import com.wenqu.ingestion.domain.TextChunk;
import com.wenqu.ingestion.repository.IngestJobRepository;
import com.wenqu.retrieval.api.ChunkDraft;
import com.wenqu.retrieval.api.RetrievalApi;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * 执行一个索引任务：导入任务先解析原文件写入正文；然后切块、向量化、替换文档的文本块。
 *
 * <p>每一步都可以安全地重复执行：原文件只解析一次（parsedAt），文本块整体替换且旧版本不覆盖新版本。
 */
@Service
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(IngestJobProcessor.class);
    private static final int MAX_ERROR_LENGTH = 300;

    private final IngestJobRepository jobRepository;
    private final DocumentApi documentApi;
    private final RetrievalApi retrievalApi;
    private final ObjectStorage objectStorage;
    private final FileParser fileParser;
    private final Chunker chunker;
    private final Clock clock;

    public IngestJobProcessor(
            IngestJobRepository jobRepository,
            DocumentApi documentApi,
            RetrievalApi retrievalApi,
            ObjectStorage objectStorage,
            FileParser fileParser,
            IngestionProperties properties,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.documentApi = documentApi;
        this.retrievalApi = retrievalApi;
        this.objectStorage = objectStorage;
        this.fileParser = fileParser;
        Encoding encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);
        this.chunker = new Chunker(
                encoding::countTokensOrdinary, properties.chunkMaxTokens(), properties.chunkOverlapTokens());
        this.clock = clock;
    }

    /**
     * @param lastAttempt 重试已用尽：本次失败时把任务标记为失败，不再抛出异常
     * @throws RuntimeException 可重试的失败（如模型或存储暂时不可用），由消息队列延迟重试
     */
    public void process(long jobId, boolean lastAttempt) {
        Optional<IngestJob> started = jobRepository.start(jobId, clock.instant());
        if (started.isEmpty()) {
            log.info("Ingest job {} already finished, skip", jobId);
            return;
        }
        IngestJob job = started.get();
        try {
            run(job);
        } catch (IngestFailure e) {
            jobRepository.finish(job.id(), JobStatus.FAILED, e.getMessage(), clock.instant());
            log.info("Ingest job {} failed: {}", job.id(), e.getMessage());
        } catch (RuntimeException e) {
            String reason = describe(e);
            if (lastAttempt) {
                jobRepository.finish(job.id(), JobStatus.FAILED, reason, clock.instant());
                log.error("Ingest job {} failed after {} attempts", job.id(), job.attempts(), e);
                return;
            }
            jobRepository.markWaitingRetry(job.id(), reason, clock.instant());
            throw e;
        }
    }

    private void run(IngestJob job) {
        if (job.trigger() == JobTrigger.IMPORT && job.parsedAt() == null) {
            importFile(job);
        }
        DocumentSnapshot snapshot = documentApi
                .findSnapshot(job.workspaceId(), job.documentId())
                .orElseThrow(() -> new IngestFailure("文档已删除"));
        OptionalInt indexed = retrievalApi.indexedVersion(job.documentId());
        if (job.trigger() == JobTrigger.EDIT && indexed.isPresent() && indexed.getAsInt() >= snapshot.version()) {
            succeed(job);
            return;
        }
        List<TextChunk> chunks = chunker.split(snapshot.blocks());
        jobRepository.startEmbedding(job.id(), chunks.size(), clock.instant());
        List<ChunkDraft> drafts = chunks.stream()
                .map(chunk -> new ChunkDraft(
                        chunk.headingPath(),
                        chunk.content(),
                        embeddingText(snapshot.title(), chunk),
                        chunk.tokenCount(),
                        chunk.blockFrom(),
                        chunk.blockTo()))
                .toList();
        retrievalApi.replaceChunks(
                job.workspaceId(),
                job.documentId(),
                snapshot.version(),
                drafts,
                embedded -> jobRepository.updateProgress(job.id(), embedded, clock.instant()));
        succeed(job);
        log.info("Indexed document {} v{} into {} chunks", job.documentId(), snapshot.version(), chunks.size());
    }

    private void importFile(IngestJob job) {
        jobRepository.updateStatus(job.id(), JobStatus.PARSING, clock.instant());
        ImportFormat format =
                ImportFormat.ofMediaType(job.mediaType()).orElseThrow(() -> new IngestFailure("不支持的文件类型"));
        List<ImportedBlock> blocks;
        try (InputStream input = objectStorage.get(job.objectKey())) {
            blocks = withoutTitleHeading(fileParser.parse(input, format), job.fileName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            documentApi
                    .replaceContent(job.workspaceId(), job.documentId(), blocks, job.createdBy())
                    .orElseThrow(() -> new IngestFailure("文档已删除"));
        } catch (BusinessException e) {
            if (e.errorCode() == ErrorCode.CONTENT_TOO_LARGE) {
                throw new IngestFailure("解析后的正文超过 2MB，请拆分文件后再导入");
            }
            throw e;
        }
        jobRepository.markParsed(job.id(), clock.instant());
    }

    /** 文件开头与文件名相同的一级标题已经作为文档标题，正文中不再重复。 */
    static List<ImportedBlock> withoutTitleHeading(List<ImportedBlock> blocks, String fileName) {
        if (!blocks.isEmpty()) {
            ImportedBlock first = blocks.getFirst();
            if (first.type() == ImportedBlock.Type.HEADING
                    && first.level() == 1
                    && first.text().strip().equals(IngestionService.title(fileName))) {
                return blocks.subList(1, blocks.size());
            }
        }
        return blocks;
    }

    private void succeed(IngestJob job) {
        jobRepository.finish(job.id(), JobStatus.SUCCEEDED, null, clock.instant());
        jobRepository.deleteFinishedIndexJobsBefore(job.workspaceId(), job.documentId(), job.id());
    }

    /**
     * 向量化的文本带上文档标题和标题路径，使「部署手册 > 回滚」下的「第二步」也能被「怎么回滚」检索到。导入文档的一级标题通常就是文档标题，不重复。
     */
    static String embeddingText(String title, TextChunk chunk) {
        String path = chunk.headingPath();
        if (!path.equals(title) && !path.startsWith(title + " > ")) {
            path = path.isEmpty() ? title : title + " > " + path;
        }
        return path + "\n\n" + chunk.content();
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String reason = "处理出错：" + message;
        return reason.length() > MAX_ERROR_LENGTH ? reason.substring(0, MAX_ERROR_LENGTH) : reason;
    }
}
