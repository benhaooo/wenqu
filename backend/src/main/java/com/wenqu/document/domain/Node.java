package com.wenqu.document.domain;

import java.time.Instant;

/** 目录树中的节点：文件夹或文档。parentId 为 null 表示位于空间根目录。 */
public record Node(
        long id, long workspaceId, Long parentId, NodeType type, String title, String position, Instant updatedAt) {

    public boolean isFolder() {
        return type == NodeType.FOLDER;
    }
}
