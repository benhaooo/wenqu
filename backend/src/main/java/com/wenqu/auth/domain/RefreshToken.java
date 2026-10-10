package com.wenqu.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh Token 记录。同一次登录后不断轮换产生的 Token 属于同一个 family。
 */
public record RefreshToken(long id, long userId, UUID familyId, Instant expiresAt, Instant revokedAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
