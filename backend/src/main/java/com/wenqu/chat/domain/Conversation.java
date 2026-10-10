package com.wenqu.chat.domain;

import java.time.Instant;

public record Conversation(long id, long workspaceId, long userId, String title, Instant updatedAt) {

    /** 新会话的标题，第一次提问后改为问题内容。 */
    public static final String DEFAULT_TITLE = "新会话";

    public static final int MAX_TITLE_LENGTH = 40;

    public static String titleFrom(String question) {
        String line = question.strip().replaceAll("\\s+", " ");
        return line.length() > MAX_TITLE_LENGTH ? line.substring(0, MAX_TITLE_LENGTH) + "…" : line;
    }
}
