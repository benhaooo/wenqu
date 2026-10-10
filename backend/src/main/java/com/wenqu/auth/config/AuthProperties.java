package com.wenqu.auth.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param jwtSecret HS256 签名密钥，至少 32 字节
 * @param cookieSecure Refresh Token Cookie 是否只在 HTTPS 下发送，部署环境为 true
 */
@Validated
@ConfigurationProperties("wenqu.auth")
public record AuthProperties(
        @NotNull @Size(min = 32) String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean cookieSecure) {}
