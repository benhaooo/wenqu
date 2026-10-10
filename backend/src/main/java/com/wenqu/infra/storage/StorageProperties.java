package com.wenqu.infra.storage;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 对象存储（S3 协议）连接配置。
 *
 * @param publicEndpoint 浏览器访问签名链接时使用的地址
 */
@Validated
@ConfigurationProperties("wenqu.storage")
public record StorageProperties(
        URI endpoint,
        URI publicEndpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket) {}
