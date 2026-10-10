package com.wenqu.document.service;

import com.wenqu.document.domain.PurgedTrash;
import com.wenqu.infra.storage.api.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天凌晨清理超过保留期的回收站内容。多实例同时执行时删除操作是幂等的。
 *
 * <p>先在事务中删除数据库记录，提交后再删除对象存储中的图片：对象删除失败只会留下无人引用的对象，不会出现引用了已删除对象的文档。
 */
@Component
class TrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);

    private final NodeService nodeService;
    private final ObjectStorage objectStorage;

    TrashPurgeJob(NodeService nodeService, ObjectStorage objectStorage) {
        this.nodeService = nodeService;
        this.objectStorage = objectStorage;
    }

    @Scheduled(cron = "0 0 3 * * *")
    void purge() {
        PurgedTrash purged = nodeService.purgeExpiredTrash();
        objectStorage.delete(purged.imageObjectKeys());
        log.info(
                "Purged {} trash nodes and {} images",
                purged.nodeCount(),
                purged.imageObjectKeys().size());
    }
}
