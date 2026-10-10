package com.wenqu.document.repository;

import static com.wenqu.jooq.document.Tables.CONTENT;
import static com.wenqu.jooq.document.Tables.NODE;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.when;

import com.wenqu.document.domain.Node;
import com.wenqu.document.domain.NodeType;
import com.wenqu.document.domain.SearchHit;
import com.wenqu.document.domain.Snippets;
import com.wenqu.document.domain.TrashItem;
import com.wenqu.jooq.document.tables.records.NodeRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class NodeRepository {

    private final DSLContext dsl;

    public NodeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 串行化同一空间内的目录结构变更（事务结束时自动释放），避免并发移动形成环或生成相同的排序键。 */
    public void lockWorkspaceTree(long workspaceId) {
        dsl.execute("select pg_advisory_xact_lock(?)", workspaceId);
    }

    public List<Node> findActiveByWorkspace(long workspaceId) {
        return dsl.selectFrom(NODE)
                .where(NODE.WORKSPACE_ID.eq(workspaceId))
                .and(NODE.DELETED_AT.isNull())
                .orderBy(NODE.PARENT_ID.nullsFirst(), NODE.POSITION, NODE.ID)
                .fetch(NodeRepository::toNode);
    }

    public Optional<Node> findActive(long workspaceId, long nodeId) {
        return dsl.selectFrom(NODE)
                .where(NODE.ID.eq(nodeId))
                .and(NODE.WORKSPACE_ID.eq(workspaceId))
                .and(NODE.DELETED_AT.isNull())
                .fetchOptional(NodeRepository::toNode);
    }

    public Node insert(long workspaceId, Long parentId, NodeType type, String title, String position, long userId) {
        Node node = dsl.insertInto(NODE)
                .set(NODE.WORKSPACE_ID, workspaceId)
                .set(NODE.PARENT_ID, parentId)
                .set(NODE.TYPE, type.name())
                .set(NODE.TITLE, title)
                .set(NODE.POSITION, position)
                .set(NODE.CREATED_BY, userId)
                .set(NODE.UPDATED_BY, userId)
                .returning()
                .fetchSingle(NodeRepository::toNode);
        if (type == NodeType.DOCUMENT) {
            dsl.insertInto(CONTENT).set(CONTENT.NODE_ID, node.id()).execute();
        }
        return node;
    }

    public void rename(long nodeId, String title, long userId, Instant now) {
        dsl.update(NODE)
                .set(NODE.TITLE, title)
                .set(NODE.UPDATED_BY, userId)
                .set(NODE.UPDATED_AT, now)
                .where(NODE.ID.eq(nodeId))
                .execute();
    }

    /** 记录最后修改人与时间（正文保存时调用）。 */
    public void touch(long nodeId, long userId, Instant now) {
        dsl.update(NODE)
                .set(NODE.UPDATED_BY, userId)
                .set(NODE.UPDATED_AT, now)
                .where(NODE.ID.eq(nodeId))
                .execute();
    }

    public void move(long nodeId, Long parentId, String position, long userId, Instant now) {
        dsl.update(NODE)
                .set(NODE.PARENT_ID, parentId)
                .set(NODE.POSITION, position)
                .set(NODE.UPDATED_BY, userId)
                .set(NODE.UPDATED_AT, now)
                .where(NODE.ID.eq(nodeId))
                .execute();
    }

    /** 同级未删除节点中排在 position 之后的第一个位置；position 为 null 时返回第一个位置。 */
    public Optional<String> findNextSiblingPosition(long workspaceId, Long parentId, String position, long excludeId) {
        Condition after = position == null ? noCondition() : NODE.POSITION.gt(position);
        return dsl.select(min(NODE.POSITION))
                .from(NODE)
                .where(siblingsOf(workspaceId, parentId))
                .and(NODE.ID.ne(excludeId))
                .and(after)
                .fetchOptional(Record1::value1);
    }

    public Optional<String> findLastSiblingPosition(long workspaceId, Long parentId) {
        return dsl.select(max(NODE.POSITION))
                .from(NODE)
                .where(siblingsOf(workspaceId, parentId))
                .fetchOptional(Record1::value1);
    }

    /** nodeId 是否为 candidateAncestorId 自身或其子孙：沿 nodeId 向上查找祖先。 */
    public boolean isSelfOrDescendant(long nodeId, long candidateAncestorId) {
        if (nodeId == candidateAncestorId) {
            return true;
        }
        CommonTableExpression<Record1<Long>> ancestors = name("ancestors")
                .fields("id")
                .as(select(NODE.PARENT_ID)
                        .from(NODE)
                        .where(NODE.ID.eq(nodeId))
                        .unionAll(select(NODE.PARENT_ID)
                                .from(NODE)
                                .join(table("ancestors"))
                                .on(NODE.ID.eq(field(name("ancestors", "id"), Long.class)))));
        return dsl.fetchExists(dsl.withRecursive(ancestors)
                .selectOne()
                .from(ancestors)
                .where(ancestors.field("id", Long.class).eq(candidateAncestorId)));
    }

    /** 把节点及其未删除的子孙标记为同一批删除，返回节点数量。 */
    public int softDeleteSubtree(long nodeId, UUID batchId, long userId, Instant now) {
        var subtree = activeSubtree(nodeId);
        return dsl.withRecursive(subtree)
                .update(NODE)
                .set(NODE.DELETED_AT, now)
                .set(NODE.DELETED_BY, userId)
                .set(NODE.DELETED_BATCH_ID, batchId)
                .where(NODE.ID.in(select(subtree.field("id", Long.class)).from(subtree)))
                .execute();
    }

    /** 回收站：每个删除批次的顶层节点（父节点不在同一批次中）。 */
    public List<TrashItem> findTrash(long workspaceId) {
        var parent = NODE.as("parent");
        var batch = NODE.as("batch");
        Field<Integer> batchSize = select(count())
                .from(batch)
                .where(batch.DELETED_BATCH_ID.eq(NODE.DELETED_BATCH_ID))
                .asField("batch_size");
        return dsl.select(NODE.asterisk(), batchSize)
                .from(NODE)
                .leftJoin(parent)
                .on(parent.ID.eq(NODE.PARENT_ID))
                .where(NODE.WORKSPACE_ID.eq(workspaceId))
                .and(NODE.DELETED_AT.isNotNull())
                .and(parent.ID.isNull().or(parent.DELETED_BATCH_ID.isDistinctFrom(NODE.DELETED_BATCH_ID)))
                .orderBy(NODE.DELETED_AT.desc(), NODE.ID)
                .fetch(record -> new TrashItem(
                        toNode(record.into(NODE)),
                        record.get(NODE.DELETED_AT),
                        record.get(NODE.DELETED_BY),
                        record.get(batchSize) - 1));
    }

    public Optional<UUID> findDeletedBatchId(long workspaceId, long nodeId) {
        return dsl.select(NODE.DELETED_BATCH_ID)
                .from(NODE)
                .where(NODE.ID.eq(nodeId))
                .and(NODE.WORKSPACE_ID.eq(workspaceId))
                .and(NODE.DELETED_AT.isNotNull())
                .fetchOptional(Record1::value1);
    }

    /** 批次中父节点不在本批次的节点（恢复时需要确认父节点是否可用）。 */
    public List<Node> findBatchRoots(UUID batchId) {
        var parent = NODE.as("parent");
        return dsl.select(NODE.asterisk())
                .from(NODE)
                .leftJoin(parent)
                .on(parent.ID.eq(NODE.PARENT_ID))
                .where(NODE.DELETED_BATCH_ID.eq(batchId))
                .and(parent.ID.isNull().or(parent.DELETED_BATCH_ID.isDistinctFrom(batchId)))
                .fetch(record -> toNode(record.into(NODE)));
    }

    public void restoreBatch(UUID batchId, long userId, Instant now) {
        dsl.update(NODE)
                .set(NODE.DELETED_AT, (Instant) null)
                .set(NODE.DELETED_BY, (Long) null)
                .set(NODE.DELETED_BATCH_ID, (UUID) null)
                .set(NODE.UPDATED_BY, userId)
                .set(NODE.UPDATED_AT, now)
                .where(NODE.DELETED_BATCH_ID.eq(batchId))
                .execute();
    }

    /** 彻底删除在 cutoff 之前进入回收站的节点（正文与子节点由外键级联删除），返回删除的节点数。 */
    public int purgeDeletedBefore(Instant cutoff) {
        return dsl.deleteFrom(NODE).where(NODE.DELETED_AT.lt(cutoff)).execute();
    }

    /** 标题或正文包含关键词的未删除文档与文件夹；标题命中的排在前面。 */
    public List<SearchHit> search(long workspaceId, String keyword, int limit) {
        String pattern = "%" + escapeLike(keyword) + "%";
        Condition titleMatches = NODE.TITLE.likeIgnoreCase(pattern, '\\');
        Condition textMatches = CONTENT.PLAIN_TEXT.likeIgnoreCase(pattern, '\\');
        return dsl.select(NODE.asterisk(), CONTENT.PLAIN_TEXT)
                .from(NODE)
                .leftJoin(CONTENT)
                .on(CONTENT.NODE_ID.eq(NODE.ID))
                .where(NODE.WORKSPACE_ID.eq(workspaceId))
                .and(NODE.DELETED_AT.isNull())
                .and(titleMatches.or(textMatches))
                .orderBy(when(titleMatches, inline(0)).otherwise(inline(1)), NODE.UPDATED_AT.desc())
                .limit(limit)
                .fetch(record -> {
                    String text = record.get(CONTENT.PLAIN_TEXT);
                    return new SearchHit(toNode(record.into(NODE)), text == null ? "" : Snippets.around(text, keyword));
                });
    }

    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Condition siblingsOf(long workspaceId, Long parentId) {
        return NODE.WORKSPACE_ID
                .eq(workspaceId)
                .and(NODE.DELETED_AT.isNull())
                .and(parentId == null ? NODE.PARENT_ID.isNull() : NODE.PARENT_ID.eq(parentId));
    }

    private static CommonTableExpression<Record1<Long>> activeSubtree(long nodeId) {
        return name("subtree")
                .fields("id")
                .as(select(NODE.ID)
                        .from(NODE)
                        .where(NODE.ID.eq(nodeId))
                        .and(NODE.DELETED_AT.isNull())
                        .unionAll(select(NODE.ID)
                                .from(NODE)
                                .join(table("subtree"))
                                .on(NODE.PARENT_ID.eq(field(name("subtree", "id"), Long.class)))
                                .where(NODE.DELETED_AT.isNull())));
    }

    private static Table<Record> table(String cteName) {
        return DSL.table(name(cteName));
    }

    private static Node toNode(NodeRecord record) {
        return new Node(
                record.getId(),
                record.getWorkspaceId(),
                record.getParentId(),
                NodeType.valueOf(record.getType()),
                record.getTitle(),
                record.getPosition(),
                record.getUpdatedAt());
    }
}
