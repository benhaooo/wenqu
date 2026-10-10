package com.wenqu.document.domain;

import java.util.Locale;

/** 从正文中截取命中关键词附近的片段。 */
public final class Snippets {

    private static final int CONTEXT = 40;

    private Snippets() {}

    public static String around(String text, String keyword) {
        String normalized = text.replaceAll("\\s+", " ").strip();
        int index = normalized.toLowerCase(Locale.ROOT).indexOf(keyword.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return truncate(normalized, 0, CONTEXT * 2);
        }
        int start = Math.max(0, index - CONTEXT);
        return truncate(normalized, start, index + keyword.length() + CONTEXT);
    }

    private static String truncate(String text, int start, int end) {
        int safeEnd = Math.min(text.length(), end);
        String prefix = start > 0 ? "…" : "";
        String suffix = safeEnd < text.length() ? "…" : "";
        return prefix + text.substring(start, safeEnd) + suffix;
    }
}
