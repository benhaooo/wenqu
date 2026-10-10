package com.wenqu.document.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** 供 ingestion、chat 使用的文档能力。调用方负责角色校验。 */
public interface DocumentApi {

    /**
     * 在 parentId 文件夹（null 为根目录）的最后新建空文档。
     *
     * @throws com.wenqu.common.error.BusinessException parentId 不是该空间中未删除的文件夹时为 INVALID_PARENT
     */
    long createDocument(long workspaceId, Long parentId, String title, long userId);

    /**
     * 用导入内容整体替换正文（不做版本校验），返回新版本；文档不存在或已删除时返回空。
     *
     * <p>不发布 {@link DocumentContentChangedEvent}：导入方随后自行建立索引。
     *
     * @throws com.wenqu.common.error.BusinessException 正文超过大小限制时为 CONTENT_TOO_LARGE
     */
    OptionalInt replaceContent(long workspaceId, long documentId, List<ImportedBlock> blocks, long userId);

    /** 未删除文档的正文快照，按顶层块拆分。 */
    Optional<DocumentSnapshot> findSnapshot(long workspaceId, long documentId);

    /** 未删除的文档 id：folderId 为 null 时为整个空间，否则为该文件夹下（含子文件夹）的文档；文件夹不存在时为空集。 */
    Set<Long> findActiveDocumentIds(long workspaceId, Long folderId);

    /** ids 中未删除的文档，已删除或不属于该空间的不出现在结果中。 */
    Map<Long, DocumentSummary> findActiveDocuments(long workspaceId, Collection<Long> ids);
}
