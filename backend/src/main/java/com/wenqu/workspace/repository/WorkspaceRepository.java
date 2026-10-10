package com.wenqu.workspace.repository;

import static com.wenqu.jooq.workspace.Tables.MEMBER;
import static com.wenqu.jooq.workspace.Tables.SPACE;

import com.wenqu.workspace.api.WorkspaceRole;
import com.wenqu.workspace.domain.MemberWorkspace;
import com.wenqu.workspace.domain.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepository {

    private final DSLContext dsl;

    public WorkspaceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Workspace insert(String name, long createdBy) {
        return dsl.insertInto(SPACE)
                .set(SPACE.NAME, name)
                .set(SPACE.CREATED_BY, createdBy)
                .returning(SPACE.ID, SPACE.NAME)
                .fetchSingle(record -> new Workspace(record.getId(), record.getName()));
    }

    public void rename(long workspaceId, String name, Instant now) {
        dsl.update(SPACE)
                .set(SPACE.NAME, name)
                .set(SPACE.UPDATED_AT, now)
                .where(SPACE.ID.eq(workspaceId))
                .execute();
    }

    public Optional<Workspace> findById(long workspaceId) {
        return dsl.selectFrom(SPACE)
                .where(SPACE.ID.eq(workspaceId))
                .fetchOptional(record -> new Workspace(record.getId(), record.getName()));
    }

    public Optional<MemberWorkspace> findForMember(long workspaceId, long userId) {
        return dsl.select(SPACE.ID, SPACE.NAME, MEMBER.ROLE)
                .from(SPACE)
                .join(MEMBER)
                .on(MEMBER.WORKSPACE_ID.eq(SPACE.ID))
                .where(SPACE.ID.eq(workspaceId))
                .and(MEMBER.USER_ID.eq(userId))
                .fetchOptional(WorkspaceRepository::toMemberWorkspace);
    }

    public List<MemberWorkspace> findAllForMember(long userId) {
        return dsl.select(SPACE.ID, SPACE.NAME, MEMBER.ROLE)
                .from(SPACE)
                .join(MEMBER)
                .on(MEMBER.WORKSPACE_ID.eq(SPACE.ID))
                .where(MEMBER.USER_ID.eq(userId))
                .orderBy(MEMBER.JOINED_AT, SPACE.ID)
                .fetch(WorkspaceRepository::toMemberWorkspace);
    }

    private static MemberWorkspace toMemberWorkspace(Record record) {
        return new MemberWorkspace(
                new Workspace(record.get(SPACE.ID), record.get(SPACE.NAME)),
                WorkspaceRole.valueOf(record.get(MEMBER.ROLE)));
    }
}
