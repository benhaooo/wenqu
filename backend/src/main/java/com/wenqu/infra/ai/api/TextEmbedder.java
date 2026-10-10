package com.wenqu.infra.ai.api;

import java.util.List;

/** 文本向量化。返回的向量维度固定为 {@link #dimensions()}。 */
public interface TextEmbedder {

    /** 当前配置的 Embedding 模型名，记录在文本块上，检索时只比较同一模型生成的向量。 */
    String model();

    int dimensions();

    /** 按输入顺序返回向量。 */
    List<float[]> embed(List<String> texts);
}
