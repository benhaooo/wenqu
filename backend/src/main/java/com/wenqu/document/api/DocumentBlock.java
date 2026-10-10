package com.wenqu.document.api;

/**
 * 正文的一个顶层块及其纯文本。
 *
 * @param index 在正文顶层块中的位置，前端据此定位与高亮
 * @param headingLevel 标题级别，非标题为 0
 */
public record DocumentBlock(int index, Kind kind, int headingLevel, String text) {

    public enum Kind {
        HEADING,
        CODE,
        TEXT
    }
}
