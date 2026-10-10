package com.wenqu.document.api;

/** 编辑器保存正文后发布，同一文档停止编辑 30 秒后才投递（发件箱防抖）。 */
public record DocumentContentChangedEvent(long workspaceId, long documentId, long userId) {

    public static final String ROUTING_KEY = "document.content-changed";
}
