package com.wenqu.chat.service;

import com.wenqu.chat.domain.AnswerEvent;
import com.wenqu.chat.domain.ChatPrompt;
import com.wenqu.chat.domain.Citation;
import com.wenqu.chat.domain.Conversation;
import com.wenqu.chat.domain.Message;
import com.wenqu.chat.domain.MessageRole;
import com.wenqu.chat.domain.MessageStatus;
import com.wenqu.chat.domain.RetrievedSource;
import com.wenqu.chat.domain.ScopeType;
import com.wenqu.chat.repository.ConversationRepository;
import com.wenqu.chat.repository.MessageRepository;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import com.wenqu.document.api.DocumentApi;
import com.wenqu.document.api.DocumentSummary;
import com.wenqu.retrieval.api.RetrievalApi;
import com.wenqu.retrieval.api.RetrievedChunk;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 基于检索的问答。校验、检索与保存提问同步完成（出错时以普通错误响应返回）；回答以事件流返回。
 *
 * <p>客户端断开连接（停止生成）时取消对模型流的订阅，对模型的 HTTP 请求随之中断，已生成的部分以 STOPPED 状态保存。
 */
@Service
@EnableConfigurationProperties(ChatProperties.class)
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    static final String MODEL_FAILURE_MESSAGE = "AI 服务调用失败，请稍后重试";

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentApi documentApi;
    private final RetrievalApi retrievalApi;
    private final ChatModel chatModel;
    private final ChatProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public AnswerService(
            ConversationService conversationService,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            DocumentApi documentApi,
            RetrievalApi retrievalApi,
            ChatModel chatModel,
            ChatProperties properties,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.documentApi = documentApi;
        this.retrievalApi = retrievalApi;
        this.chatModel = chatModel;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Flux<AnswerEvent> ask(
            long workspaceId,
            long userId,
            long conversationId,
            String question,
            ScopeType scopeType,
            Long scopeNodeId) {
        Conversation conversation = conversationService.get(workspaceId, userId, conversationId);
        Set<Long> documentIds = resolveScope(workspaceId, scopeType, scopeNodeId);
        List<Message> history = messageRepository.findRecent(conversationId, properties.historyRounds() * 2);
        List<RetrievedSource> sources =
                retrieve(workspaceId, documentIds, ChatPrompt.retrievalQuery(lastQuestion(history), question));
        transactionTemplate.executeWithoutResult(status -> {
            messageRepository.insertQuestion(conversationId, question, scopeType, scopeNodeId);
            if (Conversation.DEFAULT_TITLE.equals(conversation.title())) {
                conversationRepository.updateTitle(conversationId, Conversation.titleFrom(question));
            }
            conversationRepository.touch(conversationId, clock.instant());
        });
        return answer(conversationId, history, question, sources);
    }

    /** 删除最后一个问题之后的回答，按原范围重新检索并回答。 */
    public Flux<AnswerEvent> regenerate(long workspaceId, long userId, long conversationId) {
        conversationService.get(workspaceId, userId, conversationId);
        Message question = messageRepository
                .findLastQuestion(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTHING_TO_REGENERATE));
        Set<Long> documentIds = resolveScope(workspaceId, question.scopeType(), question.scopeNodeId());
        List<Message> history =
                messageRepository.findRecent(conversationId, properties.historyRounds() * 2 + 2).stream()
                        .filter(message -> message.id() < question.id())
                        .toList();
        List<RetrievedSource> sources = retrieve(
                workspaceId, documentIds, ChatPrompt.retrievalQuery(lastQuestion(history), question.content()));
        transactionTemplate.executeWithoutResult(status -> {
            messageRepository.deleteAfter(conversationId, question.id());
            conversationRepository.touch(conversationId, clock.instant());
        });
        return answer(conversationId, history, question.content(), sources);
    }

    private Flux<AnswerEvent> answer(
            long conversationId, List<Message> history, String question, List<RetrievedSource> sources) {
        AnswerEvent retrieval = new AnswerEvent.Retrieval(
                sourceDocuments(sources),
                sources.stream().map(RetrievedSource::toCitation).toList());
        if (sources.isEmpty()) {
            return Mono.fromCallable(() -> saveAnswer(
                            conversationId, ChatPrompt.NO_RELEVANT_CONTENT, MessageStatus.COMPLETED, List.of()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(messageId -> Flux.just(
                            retrieval,
                            new AnswerEvent.Token(ChatPrompt.NO_RELEVANT_CONTENT),
                            new AnswerEvent.Citations(List.of()),
                            new AnswerEvent.Done(messageId, MessageStatus.COMPLETED)));
        }
        StringBuilder answer = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean();
        Flux<AnswerEvent> tokens = chatModel.stream(prompt(history, question, sources))
                .map(AnswerService::text)
                .filter(text -> !text.isEmpty())
                .doOnNext(answer::append)
                .map(AnswerEvent.Token::new);
        Mono<List<AnswerEvent>> completed = Mono.fromCallable(
                        () -> settle(conversationId, answer, sources, saved, MessageStatus.COMPLETED))
                .subscribeOn(Schedulers.boundedElastic());
        return Flux.concat(Flux.just(retrieval), tokens, completed.flatMapIterable(Function.identity()))
                .onErrorResume(error -> Mono.fromCallable(() -> {
                            log.warn("Answer failed in conversation {}: {}", conversationId, error.toString());
                            List<AnswerEvent> events = new ArrayList<>(
                                    settle(conversationId, answer, sources, saved, MessageStatus.FAILED));
                            events.add(
                                    events.isEmpty() ? 0 : events.size() - 1,
                                    new AnswerEvent.Failure(MODEL_FAILURE_MESSAGE));
                            return events;
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapIterable(Function.identity()))
                .doOnCancel(() -> {
                    if (saved.get()) {
                        return;
                    }
                    log.info("Model stream cancelled by client in conversation {}", conversationId);
                    settle(conversationId, answer, sources, saved, MessageStatus.STOPPED);
                });
    }

    /** 保存回答（只保存一次），返回引用与完成事件。 */
    private List<AnswerEvent> settle(
            long conversationId,
            StringBuilder answer,
            List<RetrievedSource> sources,
            AtomicBoolean saved,
            MessageStatus status) {
        if (!saved.compareAndSet(false, true)) {
            return List.of();
        }
        String text = answer.toString();
        List<Citation> citations = ChatPrompt.citedNumbers(text, sources.size()).stream()
                .map(number -> sources.get(number - 1).toCitation())
                .toList();
        long messageId = saveAnswer(conversationId, text, status, citations);
        return List.of(new AnswerEvent.Citations(citations), new AnswerEvent.Done(messageId, status));
    }

    private long saveAnswer(long conversationId, String text, MessageStatus status, List<Citation> citations) {
        Long id = transactionTemplate.execute(transaction -> {
            long messageId = messageRepository.insertAnswer(conversationId, text, status, citations);
            conversationRepository.touch(conversationId, clock.instant());
            return messageId;
        });
        return id;
    }

    private Set<Long> resolveScope(long workspaceId, ScopeType scopeType, Long scopeNodeId) {
        if (scopeType == ScopeType.WORKSPACE) {
            return documentApi.findActiveDocumentIds(workspaceId, null);
        }
        if (scopeNodeId == null) {
            throw new BusinessException(ErrorCode.INVALID_SCOPE);
        }
        Set<Long> documentIds = scopeType == ScopeType.FOLDER
                ? documentApi.findActiveDocumentIds(workspaceId, scopeNodeId)
                : documentApi
                        .findActiveDocuments(workspaceId, List.of(scopeNodeId))
                        .keySet();
        if (documentIds.isEmpty() && scopeType == ScopeType.DOCUMENT) {
            throw new BusinessException(ErrorCode.INVALID_SCOPE);
        }
        return documentIds;
    }

    /** 检索并过滤低于阈值的文本块；文档标题取自 document 模块，已删除的文档在这里再过滤一次。 */
    private List<RetrievedSource> retrieve(long workspaceId, Set<Long> documentIds, String query) {
        List<RetrievedChunk> chunks;
        try {
            chunks = retrievalApi.search(workspaceId, documentIds, query, properties.topK());
        } catch (RuntimeException e) {
            log.warn("Retrieval failed: {}", e.toString());
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
        List<RetrievedChunk> relevant = chunks.stream()
                .filter(chunk -> chunk.similarity() >= properties.minSimilarity())
                .toList();
        Map<Long, DocumentSummary> documents = documentApi.findActiveDocuments(
                workspaceId,
                relevant.stream().map(RetrievedChunk::documentId).distinct().toList());
        List<RetrievedSource> sources = new ArrayList<>();
        for (RetrievedChunk chunk : relevant) {
            DocumentSummary document = documents.get(chunk.documentId());
            if (document != null) {
                sources.add(new RetrievedSource(
                        sources.size() + 1,
                        chunk.documentId(),
                        document.title(),
                        chunk.chunkId(),
                        chunk.headingPath(),
                        chunk.content(),
                        chunk.blockFrom(),
                        chunk.blockTo(),
                        chunk.similarity()));
            }
        }
        return sources;
    }

    private static Prompt prompt(List<Message> history, String question, List<RetrievedSource> sources) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(ChatPrompt.SYSTEM));
        for (Message message : history) {
            if (message.role() == MessageRole.USER) {
                messages.add(new UserMessage(message.content()));
            } else if (message.status() != MessageStatus.FAILED
                    && !message.content().isBlank()) {
                messages.add(new AssistantMessage(ChatPrompt.withoutMarkers(message.content())));
            }
        }
        messages.add(new UserMessage(ChatPrompt.userMessage(sources, question)));
        return new Prompt(messages);
    }

    private static List<AnswerEvent.SourceDocument> sourceDocuments(List<RetrievedSource> sources) {
        Map<Long, AnswerEvent.SourceDocument> documents = new LinkedHashMap<>();
        for (RetrievedSource source : sources) {
            documents.putIfAbsent(
                    source.documentId(), new AnswerEvent.SourceDocument(source.documentId(), source.documentTitle()));
        }
        return List.copyOf(documents.values());
    }

    private static String lastQuestion(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).role() == MessageRole.USER) {
                return history.get(i).content();
            }
        }
        return null;
    }

    private static String text(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getOutput().getText() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
