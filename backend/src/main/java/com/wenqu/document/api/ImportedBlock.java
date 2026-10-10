package com.wenqu.document.api;

/**
 * 从导入文件解析出的一个块，写入正文时转换为编辑器格式。连续的列表项合并为一个列表。
 *
 * @param level 标题级别 1～6，写入时超过 3 级的按 3 级处理；其他类型为 0
 * @param language 代码块语言，可以为 null
 */
public record ImportedBlock(Type type, int level, String text, String language) {

    public enum Type {
        HEADING,
        PARAGRAPH,
        BULLET_ITEM,
        ORDERED_ITEM,
        CODE,
        QUOTE
    }

    public static ImportedBlock heading(int level, String text) {
        return new ImportedBlock(Type.HEADING, level, text, null);
    }

    public static ImportedBlock paragraph(String text) {
        return new ImportedBlock(Type.PARAGRAPH, 0, text, null);
    }

    public static ImportedBlock bulletItem(String text) {
        return new ImportedBlock(Type.BULLET_ITEM, 0, text, null);
    }

    public static ImportedBlock orderedItem(String text) {
        return new ImportedBlock(Type.ORDERED_ITEM, 0, text, null);
    }

    public static ImportedBlock code(String language, String text) {
        return new ImportedBlock(Type.CODE, 0, text, language);
    }

    public static ImportedBlock quote(String text) {
        return new ImportedBlock(Type.QUOTE, 0, text, null);
    }
}
