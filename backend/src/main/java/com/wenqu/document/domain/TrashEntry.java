package com.wenqu.document.domain;

import java.time.Instant;

/** 回收站条目及删除人名称，用于展示。 */
public record TrashEntry(TrashItem item, Instant purgeAt, String deletedByName) {}
