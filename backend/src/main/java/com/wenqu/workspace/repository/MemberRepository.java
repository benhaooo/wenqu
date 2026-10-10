package com.wenqu.workspace.repository;

import static com.wenqu.jooq.workspace.Tables.MEMBER;

import com.wenqu.jooq.workspace.tables.records.MemberRecord;
import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.domain.Membership;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

    private final DSLContext dsl;

    public MemberRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 已是成员时不做任何修改（保留原角色），返回是否新加入。 */
    public boolean insertIfAbsent(long workspaceId, long userId, WorkspaceRole role) {
        return dsl.insertInto(MEMBER)
                        .set(MEMBER.WORKSPACE_ID, workspaceId)
                        .set(MEMBER.USER_ID, userId)
                        .set(MEMBER.ROLE, role.name())
                        .onConflictDoNothing()
                        .execute()
                > 0;
    }

    public Optional<WorkspaceRole> findRole(long workspaceId, long userId) {
        return dsl.select(MEMBER.ROLE)
                .from(MEMBER)
                .where(MEMBER.WORKSPACE_ID.eq(workspaceId))
                .and(MEMBER.USER_ID.eq(userId))
                .fetchOptional(record -> WorkspaceRole.valueOf(record.value1()));
    }

    public List<Membership> findByWorkspace(long workspaceId) {
        return dsl.selectFrom(MEMBER)
                .where(MEMBER.WORKSPACE_ID.eq(workspaceId))
                .orderBy(MEMBER.JOINED_AT, MEMBER.USER_ID)
                .fetch(MemberRepository::toMembership);
    }

    private static Membership toMembership(MemberRecord record) {
        return new Membership(
                record.getWorkspaceId(),
                record.getUserId(),
                WorkspaceRole.valueOf(record.getRole()),
                record.getJoinedAt());
    }
}
