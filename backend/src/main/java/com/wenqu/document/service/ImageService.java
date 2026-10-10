package com.wenqu.document.service;

import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.document.domain.Image;
import com.wenqu.document.domain.ImageType;
import com.wenqu.document.repository.ContentRepository;
import com.wenqu.document.repository.ImageRepository;
import com.wenqu.infra.storage.api.ObjectStorage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 文档中的图片：上传到对象存储，通过有时效的签名链接访问。 */
@Service
public class ImageService {

    static final Duration URL_TTL = Duration.ofHours(1);

    private final ImageRepository imageRepository;
    private final ContentRepository contentRepository;
    private final ObjectStorage objectStorage;
    private final Tika tika = new Tika();

    public ImageService(
            ImageRepository imageRepository, ContentRepository contentRepository, ObjectStorage objectStorage) {
        this.imageRepository = imageRepository;
        this.contentRepository = contentRepository;
        this.objectStorage = objectStorage;
    }

    /** 按文件内容检测真实类型（不信任扩展名和请求头中的类型）。 */
    public UUID upload(long workspaceId, long nodeId, MultipartFile file, long userId) throws IOException {
        contentRepository.find(workspaceId, nodeId).orElseThrow(() -> new BusinessException(ErrorCode.NODE_NOT_FOUND));
        if (file.getSize() > ImageType.MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "图片不能超过 10MB");
        }
        String mediaType;
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            mediaType = tika.detect(input);
        }
        String extension = ImageType.extensionOf(mediaType)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE));
        UUID id = UUID.randomUUID();
        String key = "workspaces/%d/images/%s.%s".formatted(workspaceId, id, extension);
        try (InputStream input = file.getInputStream()) {
            objectStorage.put(key, input, file.getSize(), mediaType);
        }
        imageRepository.insert(new Image(id, workspaceId, nodeId, key, mediaType), file.getSize(), userId);
        return id;
    }

    public URI signedUrl(long workspaceId, UUID imageId) {
        Image image = imageRepository
                .find(workspaceId, imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));
        return objectStorage.signedUrl(image.objectKey(), URL_TTL);
    }
}
