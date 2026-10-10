package com.wenqu.workspace.repository;

import static com.wenqu.jooq.workspace.Tables.INVITATION;

import com.wenqu.jooq.workspace.tables.records.InvitationRecord;
import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.domain.Invitation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationRepository {

    private final DSLContext dsl;

    public InvitationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Invitation insert(
            long workspaceId, String tokenHash, WorkspaceRole role, long createdBy, Instant expiresAt) {
        return dsl.insertInto(INVITATION)
                .set(INVITATION.WORKSPACE_ID, workspaceId)
                .set(INVITATION.TOKEN_HASH, tokenHash)
                .set(INVITATION.ROLE, role.name())
                .set(INVITATION.CREATED_BY, createdBy)
                .set(INVITATION.EXPIRES_AT, expiresAt)
                .returning()
                .fetchSingle(InvitationRepository::toInvitation);
    }

    public Optional<Invitation> findByTokenHash(String tokenHash) {
        return dsl.selectFrom(INVITATION)
                .where(INVITATION.TOKEN_HASH.eq(tokenHash))
                .fetchOptional(InvitationRepository::toInvitation);
    }

    /** 未撤销且未过期的邀请。 */
    public List<Invitation> findActiveByWorkspace(long workspaceId, Instant now) {
        return dsl.selectFrom(INVITATION)
                .where(INVITATION.WORKSPACE_ID.eq(workspaceId))
                .and(INVITATION.REVOKED_AT.isNull())
                .and(INVITATION.EXPIRES_AT.gt(now))
                .orderBy(INVITATION.CREATED_AT.desc())
                .fetch(InvitationRepository::toInvitation);
    }

    /** 返回是否撤销了一条属于该空间、且尚未撤销的邀请。 */
    public boolean revoke(long workspaceId, long invitationId, Instant now) {
        return dsl.update(INVITATION)
                        .set(INVITATION.REVOKED_AT, now)
                        .where(INVITATION.ID.eq(invitationId))
                        .and(INVITATION.WORKSPACE_ID.eq(workspaceId))
                        .and(INVITATION.REVOKED_AT.isNull())
                        .execute()
                > 0;
    }

    private static Invitation toInvitation(InvitationRecord record) {
        return new Invitation(
                record.getId(),
                record.getWorkspaceId(),
                WorkspaceRole.valueOf(record.getRole()),
                record.getCreatedBy(),
                record.getCreatedAt(),
                record.getExpiresAt(),
                record.getRevokedAt());
    }
}
