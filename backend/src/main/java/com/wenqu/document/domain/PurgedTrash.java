package com.wenqu.document.domain;

import java.util.List;

/** 一次回收站清理的结果：删除的节点数，以及需要从对象存储删除的图片。 */
public record PurgedTrash(int nodeCount, List<String> imageObjectKeys) {}
