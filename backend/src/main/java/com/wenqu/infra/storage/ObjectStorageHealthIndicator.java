package com.wenqu.infra.storage;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/** 对象存储连接状态，健康检查中的组件名为 objectStorage。 */
@Component
class ObjectStorageHealthIndicator extends AbstractHealthIndicator {

    private final S3Client s3Client;
    private final StorageProperties properties;

    ObjectStorageHealthIndicator(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        s3Client.headBucket(request -> request.bucket(properties.bucket()));
        builder.up();
    }
}
