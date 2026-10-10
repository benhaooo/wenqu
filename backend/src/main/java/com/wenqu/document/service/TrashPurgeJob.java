package com.wenqu.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨清理超过保留期的回收站内容。多实例同时执行时删除操作是幂等的。 */
@Component
class TrashPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);

    private final NodeService nodeService;

    TrashPurgeJob(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    void purge() {
        int purged = nodeService.purgeExpiredTrash();
        log.info("Purged {} expired trash nodes", purged);
    }
}
