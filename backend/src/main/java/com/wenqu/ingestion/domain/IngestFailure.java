package com.wenqu.ingestion.domain;

/** 不需要重试的失败（文件内容问题），任务直接标记为失败并展示 message。 */
public class IngestFailure extends RuntimeException {

    public IngestFailure(String message) {
        super(message);
    }
}
