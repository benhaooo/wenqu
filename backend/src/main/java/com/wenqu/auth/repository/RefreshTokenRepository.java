package com.wenqu.auth.repository;

import static com.wenqu.jooq.auth.Tables.REFRESH_TOKEN;

import com.wenqu.auth.domain.RefreshToken;
import com.wenqu.jooq.auth.tables.records.RefreshTokenRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

    private final DSLContext dsl;

    public RefreshTokenRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(long userId, String tokenHash, UUID familyId, Instant expiresAt) {
        dsl.insertInto(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.USER_ID, userId)
                .set(REFRESH_TOKEN.TOKEN_HASH, tokenHash)
                .set(REFRESH_TOKEN.FAMILY_ID, familyId)
                .set(REFRESH_TOKEN.EXPIRES_AT, expiresAt)
                .execute();
    }

    /** 加行锁读取，保证同一个 Token 的并发刷新串行执行。 */
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return dsl.selectFrom(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .forUpdate()
                .fetchOptional()
                .map(RefreshTokenRepository::toRefreshToken);
    }

    public void revoke(long id, Instant now) {
        dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, now)
                .where(REFRESH_TOKEN.ID.eq(id))
                .execute();
    }

    public void revokeFamily(UUID familyId, Instant now) {
        dsl.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, now)
                .where(REFRESH_TOKEN.FAMILY_ID.eq(familyId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    private static RefreshToken toRefreshToken(RefreshTokenRecord record) {
        return new RefreshToken(
                record.getId(), record.getUserId(), record.getFamilyId(), record.getExpiresAt(), record.getRevokedAt());
    }
}
