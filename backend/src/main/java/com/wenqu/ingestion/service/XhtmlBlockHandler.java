package com.wenqu.ingestion.service;

import com.wenqu.document.api.ImportedBlock;
import com.wenqu.ingestion.domain.LineJoiner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 把 Tika 输出的 XHTML 事件转换为块：h1～h6 为标题，p 为段落，li 为列表项，pre 为代码，blockquote 中的段落为引用，表格每行合并为一段
 * （单元格以「|」分隔）。PDF 没有标题结构，只得到段落。
 */
class XhtmlBlockHandler extends DefaultHandler {

    private final List<ImportedBlock> blocks = new ArrayList<>();
    private final Deque<String> lists = new ArrayDeque<>();
    private final StringBuilder text = new StringBuilder();
    private ImportedBlock.Type type;
    private int level;
    private int quoteDepth;
    private List<String> row;
    private StringBuilder cell;

    List<ImportedBlock> blocks() {
        flush();
        return blocks;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (localName) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> begin(ImportedBlock.Type.HEADING, localName.charAt(1) - '0');
            case "p", "div" -> {
                if (type != ImportedBlock.Type.BULLET_ITEM && type != ImportedBlock.Type.ORDERED_ITEM && cell == null) {
                    begin(quoteDepth > 0 ? ImportedBlock.Type.QUOTE : ImportedBlock.Type.PARAGRAPH, 0);
                }
            }
            case "ul", "ol" -> {
                flush();
                lists.push(localName);
            }
            case "li" ->
                begin("ol".equals(lists.peek()) ? ImportedBlock.Type.ORDERED_ITEM : ImportedBlock.Type.BULLET_ITEM, 0);
            case "pre" -> begin(ImportedBlock.Type.CODE, 0);
            case "blockquote" -> {
                flush();
                quoteDepth++;
            }
            case "tr" -> {
                flush();
                row = new ArrayList<>();
            }
            case "td", "th" -> cell = new StringBuilder();
            case "br" -> append("\n");
            default -> {}
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (localName) {
            case "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre" -> flush();
            case "p", "div" -> {
                if (type == ImportedBlock.Type.PARAGRAPH || type == ImportedBlock.Type.QUOTE) {
                    flush();
                }
            }
            case "ul", "ol" -> {
                flush();
                lists.poll();
            }
            case "blockquote" -> {
                flush();
                quoteDepth--;
            }
            case "td", "th" -> {
                if (row != null && cell != null) {
                    row.add(LineJoiner.join(cell.toString()));
                }
                cell = null;
            }
            case "tr" -> {
                if (row != null && row.stream().anyMatch(value -> !value.isBlank())) {
                    blocks.add(ImportedBlock.paragraph(String.join(" | ", row)));
                }
                row = null;
            }
            default -> {}
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        append(new String(ch, start, length));
    }

    /** Tika 把 PDF 的行尾换行作为可忽略空白输出，合并行时需要它来判断是否补空格。 */
    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        if (type != null || cell != null) {
            append(new String(ch, start, length));
        }
    }

    private void append(String value) {
        if (cell != null) {
            cell.append(value);
            return;
        }
        if (type == null) {
            if (value.isBlank()) {
                return;
            }
            type = ImportedBlock.Type.PARAGRAPH;
        }
        text.append(value);
    }

    private void begin(ImportedBlock.Type newType, int newLevel) {
        flush();
        type = newType;
        level = newLevel;
    }

    private void flush() {
        if (type != null && !text.toString().isBlank()) {
            String value = type == ImportedBlock.Type.CODE
                    ? text.toString().stripTrailing()
                    : LineJoiner.join(text.toString());
            blocks.add(new ImportedBlock(type, level, value, null));
        }
        text.setLength(0);
        type = null;
        level = 0;
    }
}
