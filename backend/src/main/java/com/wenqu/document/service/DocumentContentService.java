package com.wenqu.document.service;

import com.wenqu.auth.api.UserApi;
import com.wenqu.auth.api.UserSummary;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.document.api.DocumentContentChangedEvent;
import com.wenqu.document.api.DocumentSnapshot;
import com.wenqu.document.api.ImportedBlock;
import com.wenqu.document.domain.DocumentContent;
import com.wenqu.document.domain.ProseMirrorContent;
import com.wenqu.document.repository.ContentRepository;
import com.wenqu.document.repository.ImageRepository;
import com.wenqu.document.repository.NodeRepository;
import com.wenqu.infra.outbox.api.OutboxPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 文档正文的读取与保存。 */
@Service
public class DocumentContentService {

    /** 正文 JSON 上限，图片只存引用，2MB 足够容纳很长的文档。 */
    private static final int MAX_CONTENT_BYTES = 2 * 1024 * 1024;

    /** 停止编辑这么久之后才重新索引，避免每次自动保存都调用 Embedding 模型。 */
    static final Duration REINDEX_DEBOUNCE = Duration.ofSeconds(30);

    private final ContentRepository contentRepository;
    private final ImageRepository imageRepository;
    private final NodeRepository nodeRepository;
    private final UserApi userApi;
    private final OutboxPublisher outboxPublisher;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DocumentContentService(
            ContentRepository contentRepository,
            ImageRepository imageRepository,
            NodeRepository nodeRepository,
            UserApi userApi,
            OutboxPublisher outboxPublisher,
            JsonMapper jsonMapper,
            Clock clock) {
        this.contentRepository = contentRepository;
        this.imageRepository = imageRepository;
        this.nodeRepository = nodeRepository;
        this.userApi = userApi;
        this.outboxPublisher = outboxPublisher;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** 最后修改人的昵称，用户不存在时为空字符串。 */
    public String updatedByName(DocumentContent content) {
        UserSummary user = userApi.findByIds(List.of(content.updatedBy())).get(content.updatedBy());
        return user == null ? "" : user.displayName();
    }

    public DocumentContent get(long workspaceId, long nodeId) {
        return contentRepository
                .find(workspaceId, nodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NODE_NOT_FOUND));
    }

    /**
     * 基于 baseVersion 保存正文：内容经过净化，纯文本由服务端提取。版本不一致说明期间有其他保存，返回冲突。
     *
     * <p>同时写入防抖的 {@link DocumentContentChangedEvent}，停止编辑 30 秒后触发重新索引。
     */
    @Transactional
    public DocumentContent save(long workspaceId, long nodeId, JsonNode content, int baseVersion, long userId) {
        get(workspaceId, nodeId);
        Set<UUID> imageIds = imageRepository.findExistingIds(workspaceId, ProseMirrorContent.imageIds(content));
        ObjectNode sanitized;
        try {
            sanitized = ProseMirrorContent.sanitize(content, imageIds);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT, e.getMessage());
        }
        String json = toLimitedJson(sanitized);
        Instant now = clock.instant();
        if (!contentRepository.update(nodeId, json, ProseMirrorContent.plainText(sanitized), baseVersion, now)) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_CONFLICT);
        }
        nodeRepository.touch(nodeId, userId, now);
        outboxPublisher.publishDebounced(
                DocumentContentChangedEvent.ROUTING_KEY,
                new DocumentContentChangedEvent(workspaceId, nodeId, userId),
                DocumentContentChangedEvent.ROUTING_KEY + ":" + nodeId,
                REINDEX_DEBOUNCE);
        return get(workspaceId, nodeId);
    }

    /** 用导入内容替换正文，返回新版本；文档不存在或已删除时返回空。 */
    @Transactional
    public OptionalInt replaceWithImported(long workspaceId, long nodeId, List<ImportedBlock> blocks, long userId) {
        if (contentRepository.find(workspaceId, nodeId).isEmpty()) {
            return OptionalInt.empty();
        }
        ObjectNode document = ProseMirrorContent.sanitize(ProseMirrorContent.fromImported(blocks), Set.of());
        Instant now = clock.instant();
        int version =
                contentRepository.replace(nodeId, toLimitedJson(document), ProseMirrorContent.plainText(document), now);
        nodeRepository.touch(nodeId, userId, now);
        return OptionalInt.of(version);
    }

    public Optional<DocumentSnapshot> findSnapshot(long workspaceId, long nodeId) {
        return contentRepository
                .find(workspaceId, nodeId)
                .map(content -> new DocumentSnapshot(
                        content.nodeId(),
                        workspaceId,
                        content.title(),
                        content.version(),
                        ProseMirrorContent.blocks(jsonMapper.readTree(content.contentJson()))));
    }

    private String toLimitedJson(ObjectNode document) {
        String json = jsonMapper.writeValueAsString(document);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_LARGE);
        }
        return json;
    }
}
