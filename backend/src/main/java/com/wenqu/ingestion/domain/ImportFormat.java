package com.wenqu.ingestion.domain;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** 支持导入的文件格式，按内容检测出的媒体类型识别。 */
public enum ImportFormat {
    PDF("pdf", Set.of("application/pdf")),
    DOCX("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    DOC("doc", Set.of("application/msword")),
    MARKDOWN("md", Set.of("text/x-web-markdown", "text/markdown")),
    TEXT("txt", Set.of("text/plain"));

    public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    private final String extension;
    private final Set<String> mediaTypes;

    ImportFormat(String extension, Set<String> mediaTypes) {
        this.extension = extension;
        this.mediaTypes = mediaTypes;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImportFormat> ofMediaType(String mediaType) {
        return Arrays.stream(values())
                .filter(format -> format.mediaTypes.contains(mediaType))
                .findFirst();
    }
}
