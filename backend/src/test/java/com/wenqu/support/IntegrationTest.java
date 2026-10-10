package com.wenqu.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** 集成测试：启动完整应用，依赖由 Testcontainers 提供。所有集成测试共享同一个上下文和容器。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        properties = {
            "wenqu.auth.jwt-secret=integration-test-secret-at-least-32-characters",
            "wenqu.ai.chat.base-url=http://localhost:1/v1",
            "wenqu.ai.chat.api-key=unused",
            "wenqu.ai.chat.model=fake-chat",
            "wenqu.ai.embedding.base-url=http://localhost:1/v1",
            "wenqu.ai.embedding.api-key=unused",
            "wenqu.ai.embedding.model=fake-embedding",
            "wenqu.ai.embedding.dimensions=" + FakeEmbeddingModel.DIMENSIONS,
            "wenqu.outbox.poll-interval=PT0.2S",
            // 测试用 Embedding 按字词重合计算相似度，数值整体低于真实模型
            "wenqu.chat.min-similarity=0.1"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
