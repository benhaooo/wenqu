package com.wenqu.document.service;

import com.wenqu.document.api.DocumentApi;
import com.wenqu.document.api.DocumentSnapshot;
import com.wenqu.document.api.DocumentSummary;
import com.wenqu.document.api.ImportedBlock;
import com.wenqu.document.domain.Node;
import com.wenqu.document.domain.NodeType;
import com.wenqu.document.repository.NodeRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DocumentApiService implements DocumentApi {

    private final NodeService nodeService;
    private final DocumentContentService contentService;
    private final NodeRepository nodeRepository;

    public DocumentApiService(
            NodeService nodeService, DocumentContentService contentService, NodeRepository nodeRepository) {
        this.nodeService = nodeService;
        this.contentService = contentService;
        this.nodeRepository = nodeRepository;
    }

    @Override
    public long createDocument(long workspaceId, Long parentId, String title, long userId) {
        return nodeService
                .create(workspaceId, parentId, NodeType.DOCUMENT, title, userId)
                .id();
    }

    @Override
    public OptionalInt replaceContent(long workspaceId, long documentId, List<ImportedBlock> blocks, long userId) {
        return contentService.replaceWithImported(workspaceId, documentId, blocks, userId);
    }

    @Override
    public Optional<DocumentSnapshot> findSnapshot(long workspaceId, long documentId) {
        return contentService.findSnapshot(workspaceId, documentId);
    }

    @Override
    public Set<Long> findActiveDocumentIds(long workspaceId, Long folderId) {
        if (folderId == null) {
            return new HashSet<>(nodeRepository.findActiveDocumentIds(workspaceId));
        }
        boolean folderExists = nodeRepository
                .findActive(workspaceId, folderId)
                .filter(Node::isFolder)
                .isPresent();
        return folderExists ? new HashSet<>(nodeRepository.findActiveDocumentIdsInSubtree(folderId)) : Set.of();
    }

    /** 文件夹路径由空间内的全部未删除节点在内存中组装，团队规模下节点数在几千以内。 */
    @Override
    public Map<Long, DocumentSummary> findActiveDocuments(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Node> nodes = nodeRepository.findActiveByWorkspace(workspaceId).stream()
                .collect(Collectors.toMap(Node::id, Function.identity()));
        Map<Long, DocumentSummary> result = new HashMap<>();
        for (Long id : ids) {
            Node node = nodes.get(id);
            if (node == null || node.isFolder()) {
                continue;
            }
            List<String> path = new ArrayList<>();
            for (Node parent = nodes.get(node.parentId()); parent != null; parent = nodes.get(parent.parentId())) {
                path.add(parent.title());
            }
            Collections.reverse(path);
            result.put(id, new DocumentSummary(id, node.title(), List.copyOf(path)));
        }
        return result;
    }
}
