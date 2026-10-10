package com.wenqu.document.service;

import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.document.domain.DocumentContent;
import com.wenqu.document.domain.ProseMirrorContent;
import com.wenqu.document.repository.ContentRepository;
import com.wenqu.document.repository.ImageRepository;
import com.wenqu.document.repository.NodeRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
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

    private final ContentRepository contentRepository;
    private final ImageRepository imageRepository;
    private final NodeRepository nodeRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public DocumentContentService(
            ContentRepository contentRepository,
            ImageRepository imageRepository,
            NodeRepository nodeRepository,
            JsonMapper jsonMapper,
            Clock clock) {
        this.contentRepository = contentRepository;
        this.imageRepository = imageRepository;
        this.nodeRepository = nodeRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public DocumentContent get(long workspaceId, long nodeId) {
        return contentRepository
                .find(workspaceId, nodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NODE_NOT_FOUND));
    }

    /**
     * 基于 baseVersion 保存正文：内容经过净化，纯文本由服务端提取。版本不一致说明期间有其他保存，返回冲突。
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
        String json = jsonMapper.writeValueAsString(sanitized);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_LARGE);
        }
        Instant now = clock.instant();
        if (!contentRepository.update(nodeId, json, ProseMirrorContent.plainText(sanitized), baseVersion, now)) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_CONFLICT);
        }
        nodeRepository.touch(nodeId, userId, now);
        return get(workspaceId, nodeId);
    }
}
