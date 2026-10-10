package com.wenqu.infra.storage;

import com.wenqu.infra.storage.api.ObjectStorage;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
class S3ObjectStorage implements ObjectStorage {

    /** S3 批量删除单次最多 1000 个对象。 */
    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    S3ObjectStorage(S3Client s3Client, S3Presigner presigner, StorageProperties properties) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = properties.bucket();
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        s3Client.putObject(
                request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public URI signedUrl(String key, Duration ttl) {
        return URI.create(presigner
                .presignGetObject(request -> request.signatureDuration(ttl)
                        .getObjectRequest(get -> get.bucket(bucket).key(key)))
                .url()
                .toString());
    }

    @Override
    public void delete(Collection<String> keys) {
        List<ObjectIdentifier> objects = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        for (int start = 0; start < objects.size(); start += DELETE_BATCH_SIZE) {
            List<ObjectIdentifier> batch = objects.subList(start, Math.min(objects.size(), start + DELETE_BATCH_SIZE));
            s3Client.deleteObjects(request -> request.bucket(bucket).delete(delete -> delete.objects(batch)));
        }
    }
}
