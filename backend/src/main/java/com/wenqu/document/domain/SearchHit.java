package com.wenqu.document.domain;

/** 搜索结果：snippet 为正文中命中位置附近的片段，只命中标题时为正文开头。 */
public record SearchHit(Node node, String snippet) {}
