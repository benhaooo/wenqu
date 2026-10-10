package com.wenqu.document.domain;

import java.util.Map;
import java.util.Optional;

/** 允许上传的图片类型（按文件内容检测，不看扩展名）。 */
public final class ImageType {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONS =
            Map.of("image/png", "png", "image/jpeg", "jpg", "image/gif", "gif", "image/webp", "webp");

    private ImageType() {}

    /** 允许的类型返回扩展名。 */
    public static Optional<String> extensionOf(String detectedMediaType) {
        return Optional.ofNullable(EXTENSIONS.get(detectedMediaType));
    }
}
