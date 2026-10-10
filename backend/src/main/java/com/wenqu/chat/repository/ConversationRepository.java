package com.wenqu.chat.repository;

import static com.wenqu.jooq.chat.Tables.CONVERSATION;

import com.wenqu.chat.domain.Conversation;
import com.wenqu.jooq.chat.tables.records.ConversationRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private final DSLContext dsl;

    public ConversationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Conversation insert(long workspaceId, long userId, String title) {
        return dsl.insertInto(CONVERSATION)
                .set(CONVERSATION.WORKSPACE_ID, workspaceId)
                .set(CONVERSATION.USER_ID, userId)
                .set(CONVERSATION.TITLE, title)
                .returning()
                .fetchSingle(ConversationRepository::toConversation);
    }

    /** 会话只对创建者可见，其他人访问视为不存在。 */
    public Optional<Conversation> find(long workspaceId, long userId, long id) {
        return dsl.selectFrom(CONVERSATION)
                .where(CONVERSATION.ID.eq(id))
                .and(CONVERSATION.WORKSPACE_ID.eq(workspaceId))
                .and(CONVERSATION.USER_ID.eq(userId))
                .fetchOptional(ConversationRepository::toConversation);
    }

    public List<Conversation> findByUser(long workspaceId, long userId, int limit) {
        return dsl.selectFrom(CONVERSATION)
                .where(CONVERSATION.WORKSPACE_ID.eq(workspaceId))
                .and(CONVERSATION.USER_ID.eq(userId))
                .orderBy(CONVERSATION.UPDATED_AT.desc(), CONVERSATION.ID.desc())
                .limit(limit)
                .fetch(ConversationRepository::toConversation);
    }

    public void touch(long id, Instant now) {
        dsl.update(CONVERSATION)
                .set(CONVERSATION.UPDATED_AT, now)
                .where(CONVERSATION.ID.eq(id))
                .execute();
    }

    public void updateTitle(long id, String title) {
        dsl.update(CONVERSATION)
                .set(CONVERSATION.TITLE, title)
                .where(CONVERSATION.ID.eq(id))
                .execute();
    }

    public void delete(long id) {
        dsl.deleteFrom(CONVERSATION).where(CONVERSATION.ID.eq(id)).execute();
    }

    private static Conversation toConversation(ConversationRecord record) {
        return new Conversation(
                record.getId(), record.getWorkspaceId(), record.getUserId(), record.getTitle(), record.getUpdatedAt());
    }
}
