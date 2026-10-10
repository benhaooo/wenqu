package com.wenqu.document.repository;

import static com.wenqu.jooq.document.Tables.IMAGE;
import static com.wenqu.jooq.document.Tables.NODE;

import com.wenqu.document.domain.Image;
import com.wenqu.jooq.document.tables.records.ImageRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ImageRepository {

    private final DSLContext dsl;

    public ImageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void insert(Image image, long sizeBytes, long userId) {
        dsl.insertInto(IMAGE)
                .set(IMAGE.ID, image.id())
                .set(IMAGE.WORKSPACE_ID, image.workspaceId())
                .set(IMAGE.NODE_ID, image.nodeId())
                .set(IMAGE.OBJECT_KEY, image.objectKey())
                .set(IMAGE.CONTENT_TYPE, image.contentType())
                .set(IMAGE.SIZE_BYTES, sizeBytes)
                .set(IMAGE.CREATED_BY, userId)
                .execute();
    }

    public Optional<Image> find(long workspaceId, UUID imageId) {
        return dsl.selectFrom(IMAGE)
                .where(IMAGE.ID.eq(imageId))
                .and(IMAGE.WORKSPACE_ID.eq(workspaceId))
                .fetchOptional(ImageRepository::toImage);
    }

    /** ids 中属于该空间的图片 id。 */
    public Set<UUID> findExistingIds(long workspaceId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return dsl.select(IMAGE.ID)
                .from(IMAGE)
                .where(IMAGE.WORKSPACE_ID.eq(workspaceId))
                .and(IMAGE.ID.in(ids))
                .fetchSet(IMAGE.ID);
    }

    /** 回收站中 cutoff 之前删除的文档的图片对象，彻底删除节点前调用。 */
    public List<String> findObjectKeysOfNodesDeletedBefore(Instant cutoff) {
        return dsl.select(IMAGE.OBJECT_KEY)
                .from(IMAGE)
                .join(NODE)
                .on(NODE.ID.eq(IMAGE.NODE_ID))
                .where(NODE.DELETED_AT.lt(cutoff))
                .fetch(IMAGE.OBJECT_KEY);
    }

    private static Image toImage(ImageRecord record) {
        return new Image(
                record.getId(),
                record.getWorkspaceId(),
                record.getNodeId(),
                record.getObjectKey(),
                record.getContentType());
    }
}
