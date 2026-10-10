package com.wenqu.infra.outbox.repository;

import static com.wenqu.jooq.infra.Tables.OUTBOX_EVENT;

import com.wenqu.infra.outbox.domain.OutboxMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final DSLContext dsl;

    public OutboxRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(String routingKey, String payload, Instant availableAt) {
        dsl.insertInto(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.ROUTING_KEY, routingKey)
                .set(OUTBOX_EVENT.PAYLOAD, JSONB.valueOf(payload))
                .set(OUTBOX_EVENT.AVAILABLE_AT, availableAt)
                .execute();
    }

    /** 同一 dedupKey 未投递的事件只保留一条，更新为最新的 payload 与投递时间。 */
    public void upsertDebounced(String routingKey, String payload, String dedupKey, Instant availableAt) {
        dsl.insertInto(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.ROUTING_KEY, routingKey)
                .set(OUTBOX_EVENT.PAYLOAD, JSONB.valueOf(payload))
                .set(OUTBOX_EVENT.DEDUP_KEY, dedupKey)
                .set(OUTBOX_EVENT.AVAILABLE_AT, availableAt)
                .onConflict(OUTBOX_EVENT.DEDUP_KEY)
                .where(OUTBOX_EVENT.PUBLISHED_AT.isNull())
                .doUpdate()
                .set(OUTBOX_EVENT.ROUTING_KEY, routingKey)
                .set(OUTBOX_EVENT.PAYLOAD, JSONB.valueOf(payload))
                .set(OUTBOX_EVENT.AVAILABLE_AT, availableAt)
                .execute();
    }

    /** 锁定一批到期未投递的事件；其他实例跳过已锁定的行，不会重复投递。 */
    public List<OutboxMessage> lockDue(Instant now, int limit) {
        return dsl.select(OUTBOX_EVENT.ID, OUTBOX_EVENT.ROUTING_KEY, OUTBOX_EVENT.PAYLOAD)
                .from(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.PUBLISHED_AT.isNull())
                .and(OUTBOX_EVENT.AVAILABLE_AT.le(now))
                .orderBy(OUTBOX_EVENT.ID)
                .limit(limit)
                .forUpdate()
                .skipLocked()
                .fetch(record -> new OutboxMessage(
                        record.get(OUTBOX_EVENT.ID),
                        record.get(OUTBOX_EVENT.ROUTING_KEY),
                        record.get(OUTBOX_EVENT.PAYLOAD).data()));
    }

    public void markPublished(Collection<Long> ids, Instant now) {
        dsl.update(OUTBOX_EVENT)
                .set(OUTBOX_EVENT.PUBLISHED_AT, now)
                .where(OUTBOX_EVENT.ID.in(ids))
                .execute();
    }

    public int deletePublishedBefore(Instant cutoff) {
        return dsl.deleteFrom(OUTBOX_EVENT)
                .where(OUTBOX_EVENT.PUBLISHED_AT.lt(cutoff))
                .execute();
    }
}
