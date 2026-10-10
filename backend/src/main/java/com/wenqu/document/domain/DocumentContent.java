package com.wenqu.document.domain;

import java.time.Instant;

/** 文档正文。contentJson 为 ProseMirror 文档 JSON 字符串。 */
public record DocumentContent(long nodeId, String title, String contentJson, int version, Instant updatedAt) {}
