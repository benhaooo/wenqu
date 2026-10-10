package com.wenqu.document.domain;

import java.time.Instant;

/**
 * 回收站中的一项：一次删除操作的顶层节点。descendantCount 为同批删除的下级节点数量。
 */
public record TrashItem(Node node, Instant deletedAt, long deletedBy, int descendantCount) {}
