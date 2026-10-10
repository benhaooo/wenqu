package com.wenqu.ingestion.domain;

/** 导入列表中的一项：任务及排队位置（排在它前面的排队任务数，不在排队时为 0）。 */
public record ImportListItem(IngestJob job, int queuedAhead) {}
