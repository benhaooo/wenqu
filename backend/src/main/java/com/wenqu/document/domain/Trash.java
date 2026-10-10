package com.wenqu.document.domain;

import java.time.Duration;

public final class Trash {

    /** 回收站保留时长，超过后彻底删除。 */
    public static final Duration RETENTION = Duration.ofDays(30);

    private Trash() {}
}
