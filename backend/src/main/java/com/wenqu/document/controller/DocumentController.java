package com.wenqu.document.controller;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.common.web.ApiResult;
import com.wenqu.document.domain.DocumentContent;
import com.wenqu.document.service.DocumentContentService;
import com.wenqu.document.service.ImageService;
import com.wenqu.workspace.api.RequireWorkspaceRole;
import com.wenqu.workspace.api.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Tag(name = "document")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class DocumentController {

    private final DocumentContentService contentService;
    private final ImageService imageService;
    private final JsonMapper jsonMapper;

    public DocumentController(DocumentContentService contentService, ImageService imageService, JsonMapper jsonMapper) {
        this.contentService = contentService;
        this.imageService = imageService;
        this.jsonMapper = jsonMapper;
    }

    @Operation(operationId = "getDocument", summary = "文档标题与正文")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/documents/{nodeId}")
    public ApiResult<DocumentResponse> get(@PathVariable long workspaceId, @PathVariable long nodeId) {
        return ApiResult.ok(toResponse(contentService.get(workspaceId, nodeId)));
    }

    @Operation(operationId = "saveDocumentContent", summary = "基于 baseVersion 保存正文，版本不一致返回 409")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PutMapping("/documents/{nodeId}/content")
    public ApiResult<SavedContentResponse> save(
            @PathVariable long workspaceId,
            @PathVariable long nodeId,
            @Valid @RequestBody SaveContentRequest request,
            CurrentUser currentUser) {
        DocumentContent saved =
                contentService.save(workspaceId, nodeId, request.content(), request.baseVersion(), currentUser.id());
        return ApiResult.ok(new SavedContentResponse(saved.version(), saved.updatedAt()));
    }

    @Operation(operationId = "uploadImage", summary = "上传文档中的图片（PNG、JPEG、GIF、WebP，不超过 10MB）")
    @RequireWorkspaceRole(WorkspaceRole.EDITOR)
    @PostMapping(path = "/documents/{nodeId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ImageResponse> uploadImage(
            @PathVariable long workspaceId,
            @PathVariable long nodeId,
            @RequestPart("file") MultipartFile file,
            CurrentUser currentUser)
            throws IOException {
        return ApiResult.ok(new ImageResponse(imageService.upload(workspaceId, nodeId, file, currentUser.id())));
    }

    @Operation(operationId = "getImageUrl", summary = "图片的签名访问链接（1 小时有效）")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/images/{imageId}/url")
    public ApiResult<ImageUrlResponse> imageUrl(@PathVariable long workspaceId, @PathVariable UUID imageId) {
        return ApiResult.ok(new ImageUrlResponse(
                imageService.signedUrl(workspaceId, imageId).toString()));
    }

    private DocumentResponse toResponse(DocumentContent content) {
        return new DocumentResponse(
                content.nodeId(),
                content.title(),
                jsonMapper.readTree(content.contentJson()),
                content.version(),
                content.updatedAt());
    }

    record DocumentResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ProseMirror 文档 JSON")
            JsonNode content,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "保存时作为 baseVersion")
            int version,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant updatedAt) {}

    record SaveContentRequest(
            @NotNull(message = "正文不能为空")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ProseMirror 文档 JSON")
            JsonNode content,

            @NotNull(message = "版本不能为空") @Min(0) Integer baseVersion) {}

    record SavedContentResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int version,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant updatedAt) {}

    record ImageResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID imageId) {}

    record ImageUrlResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String url) {}
}
