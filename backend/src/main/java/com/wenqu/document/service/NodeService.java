package com.wenqu.document.service;

import com.wenqu.auth.api.UserApi;
import com.wenqu.auth.api.UserSummary;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.document.domain.FractionalIndex;
import com.wenqu.document.domain.Node;
import com.wenqu.document.domain.NodeType;
import com.wenqu.document.domain.PurgedTrash;
import com.wenqu.document.domain.SearchHit;
import com.wenqu.document.domain.Trash;
import com.wenqu.document.domain.TrashEntry;
import com.wenqu.document.domain.TrashItem;
import com.wenqu.document.repository.ImageRepository;
import com.wenqu.document.repository.NodeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 目录树：节点的创建、重命名、移动、删除与恢复。调用方已通过 {@code @RequireWorkspaceRole} 完成角色校验。
 *
 * <p>所有结构变更先获取空间级事务锁，保证环检测与排序键计算基于一致的树。
 */
@Service
public class NodeService {

    private static final int SEARCH_LIMIT = 20;

    private final NodeRepository nodeRepository;
    private final ImageRepository imageRepository;
    private final UserApi userApi;
    private final Clock clock;

    public NodeService(NodeRepository nodeRepository, ImageRepository imageRepository, UserApi userApi, Clock clock) {
        this.nodeRepository = nodeRepository;
        this.imageRepository = imageRepository;
        this.userApi = userApi;
        this.clock = clock;
    }

    public List<Node> listTree(long workspaceId) {
        return nodeRepository.findActiveByWorkspace(workspaceId);
    }

    /** 新节点排在目标文件夹的最后。 */
    @Transactional
    public Node create(long workspaceId, Long parentId, NodeType type, String title, long userId) {
        nodeRepository.lockWorkspaceTree(workspaceId);
        requireFolder(workspaceId, parentId);
        String position = FractionalIndex.between(
                nodeRepository.findLastSiblingPosition(workspaceId, parentId).orElse(null), null);
        return nodeRepository.insert(workspaceId, parentId, type, title.strip(), position, userId);
    }

    @Transactional
    public Node rename(long workspaceId, long nodeId, String title, long userId) {
        requireNode(workspaceId, nodeId);
        nodeRepository.rename(nodeId, title.strip(), userId, clock.instant());
        return requireNode(workspaceId, nodeId);
    }

    /**
     * 移动到 parentId 文件夹中、afterNodeId 之后；afterNodeId 为 null 表示放在最前。
     */
    @Transactional
    public Node move(long workspaceId, long nodeId, Long parentId, Long afterNodeId, long userId) {
        nodeRepository.lockWorkspaceTree(workspaceId);
        requireNode(workspaceId, nodeId);
        requireFolder(workspaceId, parentId);
        if (parentId != null && nodeRepository.isSelfOrDescendant(parentId, nodeId)) {
            throw new BusinessException(ErrorCode.MOVE_INTO_DESCENDANT);
        }
        String before = null;
        if (afterNodeId != null) {
            Node after = nodeRepository
                    .findActive(workspaceId, afterNodeId)
                    .filter(node -> node.id() != nodeId && Objects.equals(node.parentId(), parentId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SIBLING));
            before = after.position();
        }
        String next = nodeRepository
                .findNextSiblingPosition(workspaceId, parentId, before, nodeId)
                .orElse(null);
        nodeRepository.move(nodeId, parentId, FractionalIndex.between(before, next), userId, clock.instant());
        return requireNode(workspaceId, nodeId);
    }

    /** 节点及其子孙作为一批进入回收站。 */
    @Transactional
    public void delete(long workspaceId, long nodeId, long userId) {
        nodeRepository.lockWorkspaceTree(workspaceId);
        requireNode(workspaceId, nodeId);
        nodeRepository.softDeleteSubtree(nodeId, UUID.randomUUID(), userId, clock.instant());
    }

    public List<TrashEntry> listTrash(long workspaceId) {
        List<TrashItem> items = nodeRepository.findTrash(workspaceId);
        Map<Long, UserSummary> users = userApi.findByIds(
                items.stream().map(TrashItem::deletedBy).distinct().toList());
        return items.stream()
                .map(item -> new TrashEntry(
                        item,
                        item.deletedAt().plus(Trash.RETENTION),
                        users.containsKey(item.deletedBy())
                                ? users.get(item.deletedBy()).displayName()
                                : ""))
                .toList();
    }

    /**
     * 恢复节点所在的整个删除批次。原父文件夹已不存在或仍在回收站时，恢复到空间根目录的最后。
     */
    @Transactional
    public void restore(long workspaceId, long nodeId, long userId) {
        nodeRepository.lockWorkspaceTree(workspaceId);
        UUID batchId = nodeRepository
                .findDeletedBatchId(workspaceId, nodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRASH_ITEM_NOT_FOUND));
        Instant now = clock.instant();
        for (Node root : nodeRepository.findBatchRoots(batchId)) {
            boolean parentAvailable = root.parentId() != null
                    && nodeRepository.findActive(workspaceId, root.parentId()).isPresent();
            if (!parentAvailable) {
                String position = FractionalIndex.between(
                        nodeRepository
                                .findLastSiblingPosition(workspaceId, null)
                                .orElse(null),
                        null);
                nodeRepository.move(root.id(), null, position, userId, now);
            }
        }
        nodeRepository.restoreBatch(batchId, userId, now);
    }

    /**
     * 彻底删除超过保留期的回收站内容。返回被删除文档的图片对象，由调用方在事务提交后从对象存储删除。
     */
    @Transactional
    public PurgedTrash purgeExpiredTrash() {
        Instant cutoff = clock.instant().minus(Trash.RETENTION);
        List<String> imageKeys = imageRepository.findObjectKeysOfNodesDeletedBefore(cutoff);
        int nodes = nodeRepository.purgeDeletedBefore(cutoff);
        return new PurgedTrash(nodes, imageKeys);
    }

    public List<SearchHit> search(long workspaceId, String keyword) {
        String trimmed = keyword.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return nodeRepository.search(workspaceId, trimmed, SEARCH_LIMIT);
    }

    private Node requireNode(long workspaceId, long nodeId) {
        return nodeRepository
                .findActive(workspaceId, nodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NODE_NOT_FOUND));
    }

    /** parentId 为 null（根目录）或当前空间中未删除的文件夹。 */
    private void requireFolder(long workspaceId, Long parentId) {
        if (parentId == null) {
            return;
        }
        nodeRepository
                .findActive(workspaceId, parentId)
                .filter(Node::isFolder)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT));
    }
}
