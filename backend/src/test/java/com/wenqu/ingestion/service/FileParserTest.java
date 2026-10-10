package com.wenqu.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.wenqu.document.api.ImportedBlock;
import com.wenqu.document.api.ImportedBlock.Type;
import com.wenqu.ingestion.domain.ImportFormat;
import com.wenqu.ingestion.domain.IngestFailure;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

class FileParserTest {

    private final FileParser parser = new FileParser();

    @Test
    void markdownKeepsStructure() throws IOException {
        String markdown = """
                # 部署手册

                发布前确认
                配置已合并。

                ## 回滚

                - 切走流量
                - 回退版本
                  1. 找到上个版本
                1. 第一步

                ```Bash
                kubectl rollout undo
                ```

                > 回滚后通知值班人
                """;

        assertThat(parse(markdown, ImportFormat.MARKDOWN))
                .extracting(ImportedBlock::type, ImportedBlock::level, ImportedBlock::text, ImportedBlock::language)
                .containsExactly(
                        tuple(Type.HEADING, 1, "部署手册", null),
                        tuple(Type.PARAGRAPH, 0, "发布前确认配置已合并。", null),
                        tuple(Type.HEADING, 2, "回滚", null),
                        tuple(Type.BULLET_ITEM, 0, "切走流量", null),
                        tuple(Type.BULLET_ITEM, 0, "回退版本", null),
                        tuple(Type.ORDERED_ITEM, 0, "找到上个版本", null),
                        tuple(Type.ORDERED_ITEM, 0, "第一步", null),
                        tuple(Type.CODE, 0, "kubectl rollout undo\n", "bash"),
                        tuple(Type.QUOTE, 0, "回滚后通知值班人", null));
    }

    @Test
    void markdownSoftBreaksBetweenEnglishWordsBecomeSpaces() throws IOException {
        assertThat(parse("Release the\nnew version and wait.", ImportFormat.MARKDOWN))
                .extracting(ImportedBlock::text)
                .containsExactly("Release the new version and wait.");
    }

    @Test
    void plainTextIsSplitIntoParagraphsByBlankLines() throws IOException {
        assertThat(parse("第一段内容，介绍背景。\n\n\n第二段内容，说明做法。\r\n\r\n第三段", ImportFormat.TEXT))
                .extracting(ImportedBlock::text)
                .containsExactly("第一段内容，介绍背景。", "第二段内容，说明做法。", "第三段");
    }

    @Test
    void textThatIsNotUtf8IsDecodedAsGb18030() throws IOException {
        byte[] gbk = "这是一份用 GBK 编码保存的值班说明文档，介绍交接流程".getBytes(Charset.forName("GB18030"));

        List<ImportedBlock> blocks = parser.parse(new ByteArrayInputStream(gbk), ImportFormat.TEXT);

        assertThat(blocks).extracting(ImportedBlock::text).containsExactly("这是一份用 GBK 编码保存的值班说明文档，介绍交接流程");
    }

    @Test
    void pdfTextIsExtractedAsParagraphs() throws IOException {
        byte[] pdf = pdf("Rollback procedure: switch traffic away,", "then restore the previous release.");

        List<ImportedBlock> blocks = parser.parse(new ByteArrayInputStream(pdf), ImportFormat.PDF);

        assertThat(String.join(" ", blocks.stream().map(ImportedBlock::text).toList()))
                .contains("Rollback procedure: switch traffic away, then restore the previous release.");
    }

    @Test
    void pdfWithoutTextLooksLikeAScan() throws IOException {
        byte[] pdf = pdf();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(pdf), ImportFormat.PDF))
                .isInstanceOf(IngestFailure.class)
                .hasMessage("未识别到文字，可能是扫描件");
    }

    @Test
    void corruptedFileFailsWithoutRetry() {
        byte[] broken = "%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(broken), ImportFormat.PDF))
                .isInstanceOf(IngestFailure.class);
    }

    @Test
    void wordParagraphsAndTablesAreExtracted() throws IOException {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("值班安排说明：每周一轮换，交接时同步未关闭的告警。");
            XWPFTable table = document.createTable(1, 2);
            XWPFTableRow row = table.getRows().getFirst();
            row.getTableCells().get(0).setText("周一");
            row.getTableCells().get(1).setText("张三");
            document.write(output);
            docx = output.toByteArray();
        }

        List<ImportedBlock> blocks = parser.parse(new ByteArrayInputStream(docx), ImportFormat.DOCX);

        assertThat(blocks).extracting(ImportedBlock::text).contains("值班安排说明：每周一轮换，交接时同步未关闭的告警。", "周一 | 张三");
    }

    private List<ImportedBlock> parse(String text, ImportFormat format) throws IOException {
        return parser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), format);
    }

    /** 每行写在 PDF 的一行上；没有参数时生成一个空白页（相当于扫描件）。 */
    private static byte[] pdf(String... lines) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (lines.length > 0) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLineAtOffset(0, -16);
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
