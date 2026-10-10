package com.wenqu.ingestion.service;

import com.wenqu.document.api.ImportedBlock;
import com.wenqu.ingestion.domain.ImportFormat;
import com.wenqu.ingestion.domain.IngestFailure;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/** 把导入文件解析为块：Markdown 与纯文本自行解析，PDF 与 Word 由 Tika 输出 XHTML 后转换。 */
@Component
class FileParser {

    /** 少于这么多个非空白字符视为没有可用的文字（如扫描版 PDF）。 */
    static final int MIN_TEXT_LENGTH = 20;

    private static final Charset GB18030 = Charset.forName("GB18030");

    private final AutoDetectParser tikaParser = new AutoDetectParser();
    private final MarkdownParser markdownParser = new MarkdownParser();

    /**
     * @throws IngestFailure 文件损坏、加密或没有可识别的文字
     */
    List<ImportedBlock> parse(InputStream input, ImportFormat format) throws IOException {
        List<ImportedBlock> blocks = switch (format) {
            case MARKDOWN -> markdownParser.parse(decode(input.readAllBytes()));
            case TEXT -> plainText(decode(input.readAllBytes()));
            case PDF, DOCX, DOC -> withTika(input);
        };
        long textLength = blocks.stream()
                .mapToLong(block -> block.text()
                        .codePoints()
                        .filter(c -> !Character.isWhitespace(c))
                        .count())
                .sum();
        if (textLength < MIN_TEXT_LENGTH) {
            throw new IngestFailure(format == ImportFormat.PDF ? "未识别到文字，可能是扫描件" : "文件中没有可识别的文字");
        }
        return blocks;
    }

    private List<ImportedBlock> withTika(InputStream input) throws IOException {
        XhtmlBlockHandler handler = new XhtmlBlockHandler();
        try (TikaInputStream stream = TikaInputStream.get(input)) {
            tikaParser.parse(stream, handler, new Metadata(), new ParseContext());
        } catch (EncryptedDocumentException e) {
            throw new IngestFailure("文件已加密，请解除密码后重新导入");
        } catch (TikaException | SAXException e) {
            throw new IngestFailure("文件已损坏或格式不正确，无法解析");
        }
        return handler.blocks();
    }

    /** 空行分段。 */
    private static List<ImportedBlock> plainText(String text) {
        return Arrays.stream(text.split("\\R\\s*\\R"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .map(ImportedBlock::paragraph)
                .toList();
    }

    /** 优先按 UTF-8 解码，不是合法 UTF-8 时按 GB18030（Windows 中文文本文件常见编码）。 */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
                    .replace("﻿", "");
        } catch (CharacterCodingException e) {
            return new String(bytes, GB18030);
        }
    }
}
