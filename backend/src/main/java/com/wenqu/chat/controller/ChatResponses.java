package com.wenqu.chat.controller;

import com.wenqu.chat.domain.AnswerEvent;
import com.wenqu.chat.domain.Citation;
import com.wenqu.chat.domain.Conversation;
import com.wenqu.chat.domain.Message;
import com.wenqu.chat.domain.MessageRole;
import com.wenqu.chat.domain.MessageStatus;
import com.wenqu.chat.domain.Rating;
import com.wenqu.chat.domain.ScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

final class ChatResponses {

    private ChatResponses() {}

    record ConversationResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant updatedAt) {

        static ConversationResponse from(Conversation conversation) {
            return new ConversationResponse(conversation.id(), conversation.title(), conversation.updatedAt());
        }
    }

    record ConversationDetailResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<MessageResponse> messages) {

        static ConversationDetailResponse from(Conversation conversation, List<Message> messages) {
            return new ConversationDetailResponse(
                    conversation.id(),
                    conversation.title(),
                    messages.stream().map(MessageResponse::from).toList());
        }
    }

    record MessageResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            MessageRole role,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String content,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            MessageStatus status,

            @Schema(description = "提问的范围，只有提问有") ScopeType scopeType,
            Long scopeNodeId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "回答引用的资料，提问为空列表")
            List<CitationResponse> citations,

            @Schema(description = "对回答的评价") Rating rating,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant createdAt) {

        static MessageResponse from(Message message) {
            return new MessageResponse(
                    message.id(),
                    message.role(),
                    message.content(),
                    message.status(),
                    message.scopeType(),
                    message.scopeNodeId(),
                    message.citations().stream().map(CitationResponse::from).toList(),
                    message.rating(),
                    message.createdAt());
        }
    }

    record CitationResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "回答中的角标序号 [marker]")
            int marker,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long documentId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "回答时的文档标题")
            String documentTitle,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "所在标题路径，可能为空字符串")
            String headingPath,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "原文片段")
            String snippet,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "对应正文顶层块范围起点（含）")
            int blockFrom,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "对应正文顶层块范围终点（含）")
            int blockTo) {

        static CitationResponse from(Citation citation) {
            return new CitationResponse(
                    citation.marker(),
                    citation.documentId(),
                    citation.documentTitle(),
                    citation.headingPath(),
                    citation.snippet(),
                    citation.blockFrom(),
                    citation.blockTo());
        }
    }

    record SourceDocumentResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long documentId,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String title) {}

    enum ChatStreamEventType {
        RETRIEVAL,
        TOKEN,
        CITATIONS,
        ERROR,
        DONE
    }

    /** SSE 事件的 data，按 type 只填充对应字段。 */
    record ChatStreamEvent(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ChatStreamEventType type,

            @Schema(description = "retrieval：检索命中的文档") List<SourceDocumentResponse> documents,
            @Schema(description = "token：新生成的文本") String text,

            @Schema(description = "retrieval：提供给模型的全部资料（marker 为资料编号）；citations：回答实际引用的资料")
            List<CitationResponse> citations,

            @Schema(description = "error：失败原因") String message,
            @Schema(description = "done：保存后的回答 id") Long messageId,
            @Schema(description = "done：回答状态") MessageStatus status) {

        static ChatStreamEvent from(AnswerEvent event) {
            return switch (event) {
                case AnswerEvent.Retrieval retrieval ->
                    new ChatStreamEvent(
                            ChatStreamEventType.RETRIEVAL,
                            retrieval.documents().stream()
                                    .map(document ->
                                            new SourceDocumentResponse(document.documentId(), document.title()))
                                    .toList(),
                            null,
                            retrieval.sources().stream()
                                    .map(CitationResponse::from)
                                    .toList(),
                            null,
                            null,
                            null);
                case AnswerEvent.Token token ->
                    new ChatStreamEvent(ChatStreamEventType.TOKEN, null, token.text(), null, null, null, null);
                case AnswerEvent.Citations citations ->
                    new ChatStreamEvent(
                            ChatStreamEventType.CITATIONS,
                            null,
                            null,
                            citations.citations().stream()
                                    .map(CitationResponse::from)
                                    .toList(),
                            null,
                            null,
                            null);
                case AnswerEvent.Failure failure ->
                    new ChatStreamEvent(ChatStreamEventType.ERROR, null, null, null, failure.message(), null, null);
                case AnswerEvent.Done done ->
                    new ChatStreamEvent(
                            ChatStreamEventType.DONE, null, null, null, null, done.messageId(), done.status());
            };
        }
    }
}
