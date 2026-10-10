package com.wenqu.infra.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfiguration {

    // MinIO 不区分区域，S3 SDK 要求必须提供一个
    private static final Region REGION = Region.US_EAST_1;

    @Bean
    S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(REGION)
                .credentialsProvider(credentials(properties))
                // MinIO 使用 path-style 访问（http://host/bucket/key）
                .forcePathStyle(true)
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30)))
                .build();
    }

    /** 签名链接由浏览器直接访问，使用对外地址。 */
    @Bean
    S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.publicEndpoint())
                .region(REGION)
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
