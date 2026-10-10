package com.wenqu.ingestion.service;

import com.wenqu.document.api.ImportedBlock;
import com.wenqu.ingestion.domain.LineJoiner;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

/** Markdown 按语法树转换：标题、段落、列表（嵌套列表展平）、代码块、引用；图片和 HTML 丢弃。 */
class MarkdownParser {

    private final Parser parser = Parser.builder().build();

    List<ImportedBlock> parse(String markdown) {
        List<ImportedBlock> blocks = new ArrayList<>();
        for (Node node = parser.parse(markdown).getFirstChild(); node != null; node = node.getNext()) {
            convert(node, blocks);
        }
        return blocks;
    }

    private void convert(Node node, List<ImportedBlock> blocks) {
        switch (node) {
            case Heading heading -> blocks.add(ImportedBlock.heading(heading.getLevel(), inlineText(heading)));
            case Paragraph paragraph -> blocks.add(ImportedBlock.paragraph(inlineText(paragraph)));
            case FencedCodeBlock code -> blocks.add(ImportedBlock.code(language(code.getInfo()), code.getLiteral()));
            case IndentedCodeBlock code -> blocks.add(ImportedBlock.code(null, code.getLiteral()));
            case BulletList list -> listItems(list, false, blocks);
            case OrderedList list -> listItems(list, true, blocks);
            case BlockQuote quote -> {
                for (Node child = quote.getFirstChild(); child != null; child = child.getNext()) {
                    if (child instanceof Paragraph paragraph) {
                        blocks.add(ImportedBlock.quote(inlineText(paragraph)));
                    } else {
                        convert(child, blocks);
                    }
                }
            }
            default -> {}
        }
    }

    private void listItems(Node list, boolean ordered, List<ImportedBlock> blocks) {
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) {
                continue;
            }
            List<String> paragraphs = new ArrayList<>();
            List<ImportedBlock> nested = new ArrayList<>();
            for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Paragraph paragraph) {
                    paragraphs.add(inlineText(paragraph));
                } else {
                    convert(child, nested);
                }
            }
            String text = String.join("\n", paragraphs);
            blocks.add(ordered ? ImportedBlock.orderedItem(text) : ImportedBlock.bulletItem(text));
            blocks.addAll(nested);
        }
    }

    /** 软换行按排版换行合并，硬换行保留。 */
    private static String inlineText(Node block) {
        StringBuilder text = new StringBuilder();
        appendInline(block, text);
        StringBuilder result = new StringBuilder();
        for (String line : text.toString().split("\n", -1)) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(LineJoiner.join(line.replace('\r', '\n')));
        }
        return result.toString();
    }

    private static void appendInline(Node node, StringBuilder text) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            switch (child) {
                case Text literal -> text.append(literal.getLiteral());
                case Code code -> text.append(code.getLiteral());
                case SoftLineBreak ignored -> text.append('\r');
                case HardLineBreak ignored -> text.append('\n');
                case Image ignored -> {}
                case HtmlInline ignored -> {}
                case HtmlBlock ignored -> {}
                default -> appendInline(child, text);
            }
        }
    }

    private static String language(String info) {
        if (info == null || info.isBlank()) {
            return null;
        }
        return info.strip().split("\\s+")[0].toLowerCase(Locale.ROOT);
    }
}
