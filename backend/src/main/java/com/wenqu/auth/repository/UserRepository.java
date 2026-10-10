package com.wenqu.auth.repository;

import static com.wenqu.jooq.auth.Tables.USER_ACCOUNT;
import static org.jooq.impl.DSL.lower;

import com.wenqu.auth.domain.User;
import com.wenqu.jooq.auth.tables.records.UserAccountRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 邮箱已存在时返回空（依赖 lower(email) 唯一索引，避免并发注册的竞态）。 */
    public Optional<User> insertIfEmailAbsent(String email, String passwordHash, String displayName) {
        return dsl.insertInto(USER_ACCOUNT)
                .set(USER_ACCOUNT.EMAIL, email)
                .set(USER_ACCOUNT.PASSWORD_HASH, passwordHash)
                .set(USER_ACCOUNT.DISPLAY_NAME, displayName)
                .onConflictDoNothing()
                .returning()
                .fetchOptional()
                .map(UserRepository::toUser);
    }

    public Optional<User> findByEmail(String email) {
        return dsl.selectFrom(USER_ACCOUNT)
                .where(lower(USER_ACCOUNT.EMAIL).eq(email.toLowerCase()))
                .fetchOptional()
                .map(UserRepository::toUser);
    }

    public Optional<User> findById(long id) {
        return dsl.selectFrom(USER_ACCOUNT)
                .where(USER_ACCOUNT.ID.eq(id))
                .fetchOptional()
                .map(UserRepository::toUser);
    }

    public List<User> findByIds(Collection<Long> ids) {
        return dsl.selectFrom(USER_ACCOUNT)
                .where(USER_ACCOUNT.ID.in(ids))
                .fetch()
                .map(UserRepository::toUser);
    }

    private static User toUser(UserAccountRecord record) {
        return new User(record.getId(), record.getEmail(), record.getPasswordHash(), record.getDisplayName());
    }
}
