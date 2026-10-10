package com.wenqu.auth.domain;

import java.time.Instant;

/** 一次登录或刷新签发的令牌。refreshToken 为原文，只在此刻可见，数据库只保存其哈希。 */
public record IssuedTokens(
        User user,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {}
