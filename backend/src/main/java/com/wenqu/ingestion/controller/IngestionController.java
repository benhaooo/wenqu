package com.wenqu.ingestion.controller;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.common.web.ApiResult;
import com.wenqu.ingestion.domain.ImportListItem;
import com.wenqu.ingestion.domain.IngestJob;
import com.wenqu.ingestion.domain.JobStatus;
import com.wenqu.ingestion.service.IngestionService;
import com.wenqu.workspace.api.RequireWorkspaceRole;
import com.wenqu.workspace.api.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "ingestion", description = "文件导入与索引任务")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Operation(operationId = "importFile", summary = "导入文件（PDF、Word、Markdown、纯文本，不超过 20MB），立即在目录树中创建文档，后台解析与索引")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ImportResponse> importFile(
            @PathVariable long workspaceId,
            @RequestPart("file") MultipartFile file,
            @Schema(description = "目标文件夹，不传为根目录") @RequestParam(required = false) Long parentId,
            CurrentUser currentUser)
            throws IOException {
        IngestJob job = ingestionService.importFile(workspaceId, parentId, file, currentUser.id());
        return ApiResult.ok(ImportResponse.from(new ImportListItem(job, 0)));
    }

    @Operation(operationId = "listImports", summary = "最近 50 个导入任务及进度")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/imports")
    public ApiResult<List<ImportResponse>> listImports(@PathVariable long workspaceId) {
        return ApiResult.ok(ingestionService.listImports(workspaceId).stream()
                .map(ImportResponse::from)
                .toList());
    }

    @Operation(operationId = "retryIngestJob", summary = "重试失败的任务")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PostMapping("/ingest-jobs/{jobId}/retry")
    public ApiResult<Void> retry(@PathVariable long workspaceId, @PathVariable long jobId) {
        ingestionService.retry(workspaceId, jobId);
        return ApiResult.ok(null);
    }

    @Operation(operationId = "listIndexStatus", summary = "索引未完成的文档：处理中、等待重试或失败（每篇文档最近一次任务）")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/index-status")
    public ApiResult<List<IndexStatusResponse>> indexStatus(@PathVariable long workspaceId) {
        return ApiResult.ok(ingestionService.unfinishedJobs(workspaceId).stream()
                .map(IndexStatusResponse::from)
                .toList());
    }

    @Operation(operationId = "reindexWorkspace", summary = "重新索引空间内全部文档（更换 Embedding 模型或切块参数后使用）")
    @RequireWorkspaceRole(WorkspaceRole.ADMIN)
    @PostMapping("/reindex")
    public ApiResult<ReindexResponse> reindex(@PathVariable long workspaceId, CurrentUser currentUser) {
        return ApiResult.ok(new ReindexResponse(ingestionService.reindexWorkspace(workspaceId, currentUser.id())));
    }

    @Operation(operationId = "getSourceFile", summary = "导入文档的原文件下载链接（10 分钟有效）")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/documents/{nodeId}/source-file")
    public ApiResult<SourceFileResponse> sourceFile(@PathVariable long workspaceId, @PathVariable long nodeId) {
        IngestionService.SourceFile file = ingestionService.sourceFile(workspaceId, nodeId);
        return ApiResult.ok(new SourceFileResponse(file.fileName(), file.url().toString()));
    }

    record ImportResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long documentId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String fileName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long fileSize,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            JobStatus status,

            @Schema(description = "文本块数量，开始向量化后才有值") Integer chunkCount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已向量化的文本块数量")
            int embeddedCount,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "已开始处理的次数")
            int attempts,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "排在前面的排队任务数")
            int queuedAhead,

            @Schema(description = "失败原因；排队状态下为上次失败的原因（等待重试）")
            String errorMessage,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt) {

        static ImportResponse from(ImportListItem item) {
            IngestJob job = item.job();
            return new ImportResponse(
                    job.id(),
                    job.documentId(),
                    job.fileName(),
                    job.fileSize(),
                    job.status(),
                    job.chunkCount(),
                    job.embeddedCount(),
                    job.attempts(),
                    item.queuedAhead(),
                    job.errorMessage(),
                    job.createdAt());
        }
    }

    record IndexStatusResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long documentId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long jobId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            JobStatus status,

            String errorMessage) {

        static IndexStatusResponse from(IngestJob job) {
            return new IndexStatusResponse(job.documentId(), job.id(), job.status(), job.errorMessage());
        }
    }

    record ReindexResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "新排队的文档数")
            int queued) {}

    record SourceFileResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String fileName,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url) {}
}
