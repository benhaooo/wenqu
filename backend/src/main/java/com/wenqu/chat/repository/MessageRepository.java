package com.wenqu.chat.repository;

import static com.wenqu.jooq.chat.Tables.CITATION;
import static com.wenqu.jooq.chat.Tables.CONVERSATION;
import static com.wenqu.jooq.chat.Tables.FEEDBACK;
import static com.wenqu.jooq.chat.Tables.MESSAGE;

import com.wenqu.chat.domain.Citation;
import com.wenqu.chat.domain.Message;
import com.wenqu.chat.domain.MessageRole;
import com.wenqu.chat.domain.MessageStatus;
import com.wenqu.chat.domain.Rating;
import com.wenqu.chat.domain.ScopeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepository {

    private final DSLContext dsl;

    public MessageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long insertQuestion(long conversationId, String content, ScopeType scopeType, Long scopeNodeId) {
        return dsl.insertInto(MESSAGE)
                .set(MESSAGE.CONVERSATION_ID, conversationId)
                .set(MESSAGE.ROLE, MessageRole.USER.name())
                .set(MESSAGE.CONTENT, content)
                .set(MESSAGE.STATUS, MessageStatus.COMPLETED.name())
                .set(MESSAGE.SCOPE_TYPE, scopeType.name())
                .set(MESSAGE.SCOPE_NODE_ID, scopeNodeId)
                .returning(MESSAGE.ID)
                .fetchSingle(MESSAGE.ID);
    }

    public long insertAnswer(long conversationId, String content, MessageStatus status, List<Citation> citations) {
        long id = dsl.insertInto(MESSAGE)
                .set(MESSAGE.CONVERSATION_ID, conversationId)
                .set(MESSAGE.ROLE, MessageRole.ASSISTANT.name())
                .set(MESSAGE.CONTENT, content)
                .set(MESSAGE.STATUS, status.name())
                .returning(MESSAGE.ID)
                .fetchSingle(MESSAGE.ID);
        for (Citation citation : citations) {
            dsl.insertInto(CITATION)
                    .set(CITATION.MESSAGE_ID, id)
                    .set(CITATION.MARKER, citation.marker())
                    .set(CITATION.DOCUMENT_ID, citation.documentId())
                    .set(CITATION.DOCUMENT_TITLE, citation.documentTitle())
                    .set(CITATION.CHUNK_ID, citation.chunkId())
                    .set(CITATION.HEADING_PATH, citation.headingPath())
                    .set(CITATION.SNIPPET, citation.snippet())
                    .set(CITATION.BLOCK_FROM, citation.blockFrom())
                    .set(CITATION.BLOCK_TO, citation.blockTo())
                    .set(CITATION.SIMILARITY, (float) citation.similarity())
                    .execute();
        }
        return id;
    }

    /** 会话中的全部消息（含引用与反馈），按时间顺序。 */
    public List<Message> findByConversation(long conversationId) {
        Map<Long, List<Citation>> citations = dsl.select(CITATION.asterisk())
                .from(CITATION)
                .join(MESSAGE)
                .on(MESSAGE.ID.eq(CITATION.MESSAGE_ID))
                .where(MESSAGE.CONVERSATION_ID.eq(conversationId))
                .orderBy(CITATION.MARKER)
                .fetchGroups(CITATION.MESSAGE_ID, MessageRepository::toCitation);
        return dsl.select(MESSAGE.asterisk(), FEEDBACK.RATING)
                .from(MESSAGE)
                .leftJoin(FEEDBACK)
                .on(FEEDBACK.MESSAGE_ID.eq(MESSAGE.ID))
                .where(MESSAGE.CONVERSATION_ID.eq(conversationId))
                .orderBy(MESSAGE.ID)
                .fetch(record -> toMessage(record, citations.getOrDefault(record.get(MESSAGE.ID), List.of())));
    }

    /** 最近 limit 条消息（不含引用），按时间顺序。 */
    public List<Message> findRecent(long conversationId, int limit) {
        List<Message> recent = new ArrayList<>(dsl.select(MESSAGE.asterisk(), FEEDBACK.RATING)
                .from(MESSAGE)
                .leftJoin(FEEDBACK)
                .on(FEEDBACK.MESSAGE_ID.eq(MESSAGE.ID))
                .where(MESSAGE.CONVERSATION_ID.eq(conversationId))
                .orderBy(MESSAGE.ID.desc())
                .limit(limit)
                .fetch(record -> toMessage(record, List.of())));
        Collections.reverse(recent);
        return recent;
    }

    /** 删除 afterId 之后的消息（重新生成回答前删除旧回答）。 */
    public void deleteAfter(long conversationId, long afterId) {
        dsl.deleteFrom(MESSAGE)
                .where(MESSAGE.CONVERSATION_ID.eq(conversationId))
                .and(MESSAGE.ID.gt(afterId))
                .execute();
    }

    /** 用户自己会话中的回答。 */
    public boolean isOwnAnswer(long workspaceId, long userId, long messageId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(MESSAGE)
                .join(CONVERSATION)
                .on(CONVERSATION.ID.eq(MESSAGE.CONVERSATION_ID))
                .where(MESSAGE.ID.eq(messageId))
                .and(MESSAGE.ROLE.eq(MessageRole.ASSISTANT.name()))
                .and(CONVERSATION.WORKSPACE_ID.eq(workspaceId))
                .and(CONVERSATION.USER_ID.eq(userId)));
    }

    public void saveFeedback(long messageId, Rating rating, Instant now) {
        dsl.insertInto(FEEDBACK)
                .set(FEEDBACK.MESSAGE_ID, messageId)
                .set(FEEDBACK.RATING, rating.name())
                .onConflict(FEEDBACK.MESSAGE_ID)
                .doUpdate()
                .set(FEEDBACK.RATING, rating.name())
                .set(FEEDBACK.UPDATED_AT, now)
                .execute();
    }

    public void deleteFeedback(long messageId) {
        dsl.deleteFrom(FEEDBACK).where(FEEDBACK.MESSAGE_ID.eq(messageId)).execute();
    }

    public Optional<Message> findLastQuestion(long conversationId) {
        return dsl.select(MESSAGE.asterisk(), FEEDBACK.RATING)
                .from(MESSAGE)
                .leftJoin(FEEDBACK)
                .on(FEEDBACK.MESSAGE_ID.eq(MESSAGE.ID))
                .where(MESSAGE.CONVERSATION_ID.eq(conversationId))
                .and(MESSAGE.ROLE.eq(MessageRole.USER.name()))
                .orderBy(MESSAGE.ID.desc())
                .limit(1)
                .fetchOptional(record -> toMessage(record, List.of()));
    }

    private static Message toMessage(Record record, List<Citation> citations) {
        String scopeType = record.get(MESSAGE.SCOPE_TYPE);
        String rating = record.get(FEEDBACK.RATING);
        return new Message(
                record.get(MESSAGE.ID),
                MessageRole.valueOf(record.get(MESSAGE.ROLE)),
                record.get(MESSAGE.CONTENT),
                MessageStatus.valueOf(record.get(MESSAGE.STATUS)),
                scopeType == null ? null : ScopeType.valueOf(scopeType),
                record.get(MESSAGE.SCOPE_NODE_ID),
                citations,
                rating == null ? null : Rating.valueOf(rating),
                record.get(MESSAGE.CREATED_AT));
    }

    private static Citation toCitation(Record record) {
        return new Citation(
                record.get(CITATION.MARKER),
                record.get(CITATION.DOCUMENT_ID),
                record.get(CITATION.DOCUMENT_TITLE),
                record.get(CITATION.CHUNK_ID),
                record.get(CITATION.HEADING_PATH),
                record.get(CITATION.SNIPPET),
                record.get(CITATION.BLOCK_FROM),
                record.get(CITATION.BLOCK_TO),
                record.get(CITATION.SIMILARITY));
    }
}
