package com.wenqu.infra.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/** 启动时确保存储桶存在，新环境无需手动创建。 */
@Component
class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

    private final S3Client s3Client;
    private final StorageProperties properties;

    BucketInitializer(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.bucket();
        try {
            s3Client.headBucket(request -> request.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(request -> request.bucket(bucket));
            log.info("Created bucket {}", bucket);
        }
    }
}
