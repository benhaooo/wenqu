package com.wenqu.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** 测试用对话模型：按设定的片段流式返回，记录收到的提示词以及流是否被取消。 */
public class FakeChatModel implements ChatModel {

    public static final List<String> DEFAULT_ANSWER = List.of("根据资料，", "答案在这里", "[1]。");

    private final AtomicReference<List<String>> tokens = new AtomicReference<>(DEFAULT_ANSWER);
    private final AtomicReference<Duration> delay = new AtomicReference<>(Duration.ZERO);
    private final AtomicBoolean fail = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();

    public void answer(List<String> nextTokens, Duration tokenDelay) {
        tokens.set(nextTokens);
        delay.set(tokenDelay);
    }

    /** 输出完设定的片段后报错，模拟模型中途失败。 */
    public void failAfterTokens() {
        fail.set(true);
    }

    public List<Prompt> prompts() {
        return prompts;
    }

    public boolean cancelled() {
        return cancelled.get();
    }

    public void reset() {
        tokens.set(DEFAULT_ANSWER);
        delay.set(Duration.ZERO);
        fail.set(false);
        cancelled.set(false);
        prompts.clear();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        return response(String.join("", tokens.get()));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        prompts.add(prompt);
        Flux<ChatResponse> stream = Flux.fromIterable(tokens.get()).map(FakeChatModel::response);
        if (!delay.get().isZero()) {
            stream = stream.delayElements(delay.get());
        }
        if (fail.get()) {
            stream = stream.concatWith(Flux.error(new IllegalStateException("simulated model failure")));
        }
        return stream.doOnCancel(() -> cancelled.set(true));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
