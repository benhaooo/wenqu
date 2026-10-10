package com.wenqu.chat.controller;

import com.wenqu.auth.api.CurrentUser;
import com.wenqu.chat.controller.ChatResponses.ChatStreamEvent;
import com.wenqu.chat.controller.ChatResponses.ConversationDetailResponse;
import com.wenqu.chat.controller.ChatResponses.ConversationResponse;
import com.wenqu.chat.domain.AnswerEvent;
import com.wenqu.chat.domain.Conversation;
import com.wenqu.chat.domain.Rating;
import com.wenqu.chat.domain.ScopeType;
import com.wenqu.chat.service.AnswerService;
import com.wenqu.chat.service.ConversationService;
import com.wenqu.common.web.ApiResult;
import com.wenqu.workspace.api.RequireWorkspaceRole;
import com.wenqu.workspace.api.WorkspaceRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "chat", description = "AI 问答")
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class ChatController {

    private final ConversationService conversationService;
    private final AnswerService answerService;

    public ChatController(ConversationService conversationService, AnswerService answerService) {
        this.conversationService = conversationService;
        this.answerService = answerService;
    }

    @Operation(operationId = "listConversations", summary = "我在该空间的最近 50 个会话")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/conversations")
    public ApiResult<List<ConversationResponse>> list(@PathVariable long workspaceId, CurrentUser currentUser) {
        return ApiResult.ok(conversationService.list(workspaceId, currentUser.id()).stream()
                .map(ConversationResponse::from)
                .toList());
    }

    @Operation(operationId = "createConversation", summary = "新建会话")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @PostMapping("/conversations")
    public ApiResult<ConversationResponse> create(@PathVariable long workspaceId, CurrentUser currentUser) {
        return ApiResult.ok(ConversationResponse.from(conversationService.create(workspaceId, currentUser.id())));
    }

    @Operation(operationId = "getConversation", summary = "会话中的全部消息、引用与反馈")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @GetMapping("/conversations/{conversationId}")
    public ApiResult<ConversationDetailResponse> get(
            @PathVariable long workspaceId, @PathVariable long conversationId, CurrentUser currentUser) {
        Conversation conversation = conversationService.get(workspaceId, currentUser.id(), conversationId);
        return ApiResult.ok(ConversationDetailResponse.from(
                conversation, conversationService.messages(workspaceId, currentUser.id(), conversationId)));
    }

    @Operation(operationId = "deleteConversation", summary = "删除会话")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @DeleteMapping("/conversations/{conversationId}")
    public ApiResult<Void> delete(
            @PathVariable long workspaceId, @PathVariable long conversationId, CurrentUser currentUser) {
        conversationService.delete(workspaceId, currentUser.id(), conversationId);
        return ApiResult.ok(null);
    }

    @Operation(
            operationId = "askQuestion",
            summary = "提问，回答以 SSE 流式返回",
            description = "事件依次为 retrieval、若干 token、citations、（失败时）error、done；每个事件的 data 是 ChatStreamEvent JSON。"
                    + "校验或检索失败时返回普通 JSON 错误响应。客户端断开连接即停止生成。",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            content =
                                    @Content(
                                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                            schema = @Schema(implementation = ChatStreamEvent.class))))
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @PostMapping(path = "/conversations/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> ask(
            @PathVariable long workspaceId,
            @PathVariable long conversationId,
            @Valid @RequestBody AskRequest request,
            CurrentUser currentUser) {
        return toSse(answerService.ask(
                workspaceId,
                currentUser.id(),
                conversationId,
                request.question().strip(),
                request.scopeType(),
                request.scopeNodeId()));
    }

    @Operation(
            operationId = "regenerateAnswer",
            summary = "重新生成最后一个问题的回答，事件格式同 askQuestion",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            content =
                                    @Content(
                                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                            schema = @Schema(implementation = ChatStreamEvent.class))))
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @PostMapping(path = "/conversations/{conversationId}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> regenerate(
            @PathVariable long workspaceId, @PathVariable long conversationId, CurrentUser currentUser) {
        return toSse(answerService.regenerate(workspaceId, currentUser.id(), conversationId));
    }

    @Operation(operationId = "rateAnswer", summary = "评价回答，rating 为 null 时取消评价")
    @RequireWorkspaceRole(WorkspaceRole.VIEWER)
    @PutMapping("/messages/{messageId}/feedback")
    public ApiResult<Void> rate(
            @PathVariable long workspaceId,
            @PathVariable long messageId,
            @RequestBody FeedbackRequest request,
            CurrentUser currentUser) {
        conversationService.rate(workspaceId, currentUser.id(), messageId, request.rating());
        return ApiResult.ok(null);
    }

    private static Flux<ServerSentEvent<ChatStreamEvent>> toSse(Flux<AnswerEvent> events) {
        return events.map(event -> {
            ChatStreamEvent data = ChatStreamEvent.from(event);
            return ServerSentEvent.builder(data)
                    .event(data.type().name().toLowerCase())
                    .build();
        });
    }

    record AskRequest(
            @NotBlank(message = "问题不能为空")
            @Size(max = 2000, message = "问题不能超过 2000 字")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String question,

            @NotNull(message = "提问范围不能为空") @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ScopeType scopeType,

            @Schema(description = "scopeType 为 FOLDER 或 DOCUMENT 时必填")
            Long scopeNodeId) {}

    record FeedbackRequest(Rating rating) {}
}
