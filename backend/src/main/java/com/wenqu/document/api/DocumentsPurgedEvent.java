package com.wenqu.document.api;

import java.util.List;

/** 回收站中的文档被彻底删除后发布。移入回收站时不发布：下游按 {@link DocumentApi} 的未删除文档过滤。 */
public record DocumentsPurgedEvent(List<Long> documentIds) {

    public static final String ROUTING_KEY = "document.purged";
}
