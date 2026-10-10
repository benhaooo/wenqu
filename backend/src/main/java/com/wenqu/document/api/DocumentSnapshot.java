package com.wenqu.document.api;

import java.util.List;

/** 某一版本的正文，blocks 与编辑器中的顶层块一一对应（index 从 0 开始）。 */
public record DocumentSnapshot(long id, long workspaceId, String title, int version, List<DocumentBlock> blocks) {}
