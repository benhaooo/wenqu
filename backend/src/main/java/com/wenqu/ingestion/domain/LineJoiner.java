package com.wenqu.ingestion.domain;

import java.util.regex.Pattern;

/**
 * 合并 PDF 和 Markdown 中因排版产生的换行：中文之间直接相连，西文之间补一个空格，行末连字符断开的单词重新拼合。
 */
public final class LineJoiner {

    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00a0\\u3000]+");

    private LineJoiner() {}

    public static String join(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (String rawLine : text.split("\\R")) {
            String line = SPACES.matcher(rawLine).replaceAll(" ").strip();
            if (line.isEmpty()) {
                continue;
            }
            if (result.isEmpty()) {
                result.append(line);
                continue;
            }
            char last = result.charAt(result.length() - 1);
            char next = line.charAt(0);
            if (last == '-'
                    && result.length() > 1
                    && Character.isLetter(result.charAt(result.length() - 2))
                    && Character.isLowerCase(next)) {
                result.setLength(result.length() - 1);
            } else if (isLatin(last) && isLatin(next)) {
                result.append(' ');
            }
            result.append(line);
        }
        return result.toString();
    }

    /** ASCII 字符（字母、数字、标点）之间需要空格。 */
    private static boolean isLatin(char c) {
        return c < 0x80;
    }
}
