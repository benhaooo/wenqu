package com.wenqu.chat.domain;

import java.time.Instant;
import java.util.List;

/**
 * 会话中的一条消息。scopeType、scopeNodeId 只有提问有；citations、rating 只有回答有。
 */
public record Message(
        long id,
        MessageRole role,
        String content,
        MessageStatus status,
        ScopeType scopeType,
        Long scopeNodeId,
        List<Citation> citations,
        Rating rating,
        Instant createdAt) {}
