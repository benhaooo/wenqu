package com.wenqu.ingestion.domain;

public enum JobStatus {
    /** 等待处理，包括失败后等待延迟重试。 */
    QUEUED,
    PARSING,
    EMBEDDING,
    SUCCEEDED,
    FAILED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED;
    }
}
