package com.wenqu.document.repository;

import static com.wenqu.jooq.document.Tables.CONTENT;
import static com.wenqu.jooq.document.Tables.NODE;

import com.wenqu.document.domain.DocumentContent;
import com.wenqu.document.domain.NodeType;
import java.time.Instant;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class ContentRepository {

    private final DSLContext dsl;

    public ContentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** 未删除文档的正文。 */
    public Optional<DocumentContent> find(long workspaceId, long nodeId) {
        return dsl.select(NODE.ID, NODE.TITLE, CONTENT.CONTENT_JSON, CONTENT.VERSION, NODE.UPDATED_AT, NODE.UPDATED_BY)
                .from(NODE)
                .join(CONTENT)
                .on(CONTENT.NODE_ID.eq(NODE.ID))
                .where(NODE.ID.eq(nodeId))
                .and(NODE.WORKSPACE_ID.eq(workspaceId))
                .and(NODE.TYPE.eq(NodeType.DOCUMENT.name()))
                .and(NODE.DELETED_AT.isNull())
                .fetchOptional(record -> new DocumentContent(
                        record.get(NODE.ID),
                        record.get(NODE.TITLE),
                        record.get(CONTENT.CONTENT_JSON).data(),
                        record.get(CONTENT.VERSION),
                        record.get(NODE.UPDATED_AT),
                        record.get(NODE.UPDATED_BY)));
    }

    /** 仅当当前版本等于 baseVersion 时保存，返回是否保存成功。 */
    public boolean update(long nodeId, String contentJson, String plainText, int baseVersion, Instant now) {
        return dsl.update(CONTENT)
                        .set(CONTENT.CONTENT_JSON, JSONB.valueOf(contentJson))
                        .set(CONTENT.PLAIN_TEXT, plainText)
                        .set(CONTENT.VERSION, CONTENT.VERSION.plus(1))
                        .set(CONTENT.UPDATED_AT, now)
                        .where(CONTENT.NODE_ID.eq(nodeId))
                        .and(CONTENT.VERSION.eq(baseVersion))
                        .execute()
                > 0;
    }
}
