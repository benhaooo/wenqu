package com.wenqu.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.zip.CRC32;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 测试用 Embedding：把文本中的单字和相邻两字哈希到固定维度并归一化，共享字词越多余弦相似度越高，结果确定且不调用外部服务。
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 1024;

    private final AtomicInteger failuresToSimulate = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();

    /** 接下来的 count 次调用抛出异常，模拟模型服务不可用。 */
    public void failNext(int count) {
        failuresToSimulate.set(count);
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        failuresToSimulate.set(0);
        calls.set(0);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        if (failuresToSimulate.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new IllegalStateException("simulated embedding outage");
        }
        List<String> texts = request.getInstructions();
        return new EmbeddingResponse(IntStream.range(0, texts.size())
                .mapToObj(i -> new Embedding(vector(texts.get(i)), i))
                .toList());
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public static float[] vector(String text) {
        float[] vector = new float[DIMENSIONS];
        String normalized = text.toLowerCase();
        int[] codePoints =
                normalized.codePoints().filter(Character::isLetterOrDigit).toArray();
        for (int i = 0; i < codePoints.length; i++) {
            vector[bucket(Character.toString(codePoints[i]))] += 1;
            if (i + 1 < codePoints.length) {
                vector[bucket(Character.toString(codePoints[i]) + Character.toString(codePoints[i + 1]))] += 2;
            }
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            vector[0] = 1;
            return vector;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    private static int bucket(String gram) {
        CRC32 crc = new CRC32();
        crc.update(gram.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % DIMENSIONS);
    }
}
