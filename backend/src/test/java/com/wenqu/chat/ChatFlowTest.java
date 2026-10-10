package com.wenqu.chat;

import static com.wenqu.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wenqu.chat.domain.AnswerEvent;
import com.wenqu.chat.domain.ChatPrompt;
import com.wenqu.chat.domain.ScopeType;
import com.wenqu.chat.service.AnswerService;
import com.wenqu.support.FakeChatModel;
import com.wenqu.support.FakeEmbeddingModel;
import com.wenqu.support.IntegrationTest;
import com.wenqu.support.TestApi;
import com.wenqu.support.TestApi.LoggedInUser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class ChatFlowTest {

    private static final String RUNBOOK = """
            # 部署手册

            ## 回滚

            线上出现故障时，先把流量切到旧集群，再执行 kubectl rollout undo 回滚到上一个版本。
            """;

    private static final String ONBOARDING = """
            # 新人入门

            第一天领取电脑，开通邮箱和代码仓库权限，找导师过一遍项目结构。
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FakeChatModel chatModel;

    @Autowired
    FakeEmbeddingModel embeddingModel;

    @Autowired
    AnswerService answerService;

    LoggedInUser member;
    long workspaceId;
    long runbookId;
    long onboardingId;
    long conversationId;

    @BeforeEach
    void setUp() throws Exception {
        member = TestApi.register(mockMvc);
        workspaceId = TestApi.createWorkspace(mockMvc, member);
        runbookId = TestApi.importAndIndex(mockMvc, workspaceId, member, "部署手册.md", RUNBOOK);
        onboardingId = TestApi.importAndIndex(mockMvc, workspaceId, member, "新人入门.md", ONBOARDING);
        conversationId = createConversation(member);
    }

    @AfterEach
    void tearDown() {
        chatModel.reset();
        embeddingModel.reset();
    }

    @Test
    void answerIsStreamedWithRetrievalTokensCitationsAndDone() throws Exception {
        List<Map<String, Object>> events = ask(member, "线上故障怎么回滚到上一个版本", "WORKSPACE", null);

        assertThat(events)
                .extracting(event -> event.get("type"))
                .containsExactly("RETRIEVAL", "TOKEN", "TOKEN", "TOKEN", "CITATIONS", "DONE");
        assertThat(JsonPath.<List<Integer>>read(events.getFirst(), "$.documents[*].documentId"))
                .first()
                .isEqualTo((int) runbookId);
        assertThat(JsonPath.<Integer>read(events.get(4), "$.citations[0].marker"))
                .isEqualTo(1);
        assertThat(JsonPath.<String>read(events.get(4), "$.citations[0].headingPath"))
                .isEqualTo("回滚");
        assertThat(JsonPath.<List<Integer>>read(events.getFirst(), "$.citations[*].marker"))
                .startsWith(1);
        assertThat(events.getLast()).containsEntry("status", "COMPLETED");

        List<Message> sent = chatModel.prompts().getLast().getInstructions();
        assertThat(sent.getFirst().getText()).isEqualTo(ChatPrompt.SYSTEM);
        assertThat(sent.getLast().getText()).contains("[1] 《部署手册》 回滚").endsWith("问题：线上故障怎么回滚到上一个版本");

        mockMvc.perform(member.authorize(get("/api/workspaces/{w}/conversations/{c}", workspaceId, conversationId)))
                .andExpect(jsonPath("$.data.title").value("线上故障怎么回滚到上一个版本"))
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.data.messages[0].scopeType").value("WORKSPACE"))
                .andExpect(jsonPath("$.data.messages[1].content").value("根据资料，答案在这里[1]。"))
                .andExpect(jsonPath("$.data.messages[1].citations[0].documentTitle")
                        .value("部署手册"))
                .andExpect(jsonPath("$.data.messages[1].citations[0].blockFrom").value(1));
    }

    @Test
    void followUpQuestionCarriesHistoryAndPreviousQuestionForRetrieval() throws Exception {
        ask(member, "线上故障怎么回滚", "WORKSPACE", null);

        ask(member, "需要执行什么命令", "WORKSPACE", null);

        List<Message> sent = chatModel.prompts().getLast().getInstructions();
        assertThat(sent).hasSize(4);
        assertThat(sent.get(1).getText()).isEqualTo("线上故障怎么回滚");
        assertThat(sent.get(2).getText()).isEqualTo("根据资料，答案在这里。");
        assertThat(sent.get(3).getText()).contains("《部署手册》");
    }

    @Test
    void unrelatedQuestionIsAnsweredWithoutCallingTheModel() throws Exception {
        List<Map<String, Object>> events = ask(member, "量子力学中的薛定谔方程", "WORKSPACE", null);

        assertThat(events)
                .extracting(event -> event.get("type"))
                .containsExactly("RETRIEVAL", "TOKEN", "CITATIONS", "DONE");
        assertThat(events.get(1)).containsEntry("text", ChatPrompt.NO_RELEVANT_CONTENT);
        assertThat(chatModel.prompts()).isEmpty();
    }

    @Test
    void scopeLimitsWhichDocumentsAreSearched() throws Exception {
        List<Map<String, Object>> events = ask(member, "线上故障怎么回滚", "DOCUMENT", onboardingId);

        assertThat(events.get(1)).containsEntry("text", ChatPrompt.NO_RELEVANT_CONTENT);

        mockMvc.perform(member.authorize(
                                post("/api/workspaces/{w}/conversations/{c}/messages", workspaceId, conversationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'question':'回滚','scopeType':'DOCUMENT','scopeNodeId':999999}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(16003));
    }

    @Test
    void deletedDocumentsAreNeverCited() throws Exception {
        mockMvc.perform(member.authorize(delete("/api/workspaces/{w}/nodes/{n}", workspaceId, runbookId)))
                .andExpect(status().isOk());

        List<Map<String, Object>> events = ask(member, "线上故障怎么回滚到上一个版本", "WORKSPACE", null);

        assertThat(events.get(1)).containsEntry("text", ChatPrompt.NO_RELEVANT_CONTENT);
    }

    @Test
    void regenerateReplacesTheLastAnswer() throws Exception {
        ask(member, "线上故障怎么回滚", "WORKSPACE", null);
        chatModel.answer(List.of("新的回答[1]"), Duration.ZERO);

        MvcResult started = mockMvc.perform(member.authorize(
                        post("/api/workspaces/{w}/conversations/{c}/regenerate", workspaceId, conversationId)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        mockMvc.perform(member.authorize(get("/api/workspaces/{w}/conversations/{c}", workspaceId, conversationId)))
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[1].content").value("新的回答[1]"));
    }

    @Test
    void stoppingGenerationCancelsTheModelAndKeepsThePartialAnswer() throws Exception {
        chatModel.answer(Collections.nCopies(20, "片段"), Duration.ofMillis(100));

        List<AnswerEvent> received = answerService
                .ask(workspaceId, member.id(), conversationId, "怎么回滚", ScopeType.WORKSPACE, null)
                .take(3)
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(received).hasSize(3);
        assertThat(chatModel.cancelled()).isTrue();
        mockMvc.perform(member.authorize(get("/api/workspaces/{w}/conversations/{c}", workspaceId, conversationId)))
                .andExpect(jsonPath("$.data.messages[1].status").value("STOPPED"))
                .andExpect(jsonPath("$.data.messages[1].content").value("片段片段"));
    }

    @Test
    void modelFailureIsReportedAndThePartialAnswerKept() throws Exception {
        chatModel.answer(List.of("部分回答"), Duration.ZERO);
        chatModel.failAfterTokens();

        List<Map<String, Object>> events = ask(member, "怎么回滚", "WORKSPACE", null);

        assertThat(events)
                .extracting(event -> event.get("type"))
                .containsExactly("RETRIEVAL", "TOKEN", "CITATIONS", "ERROR", "DONE");
        assertThat(events.getLast()).containsEntry("status", "FAILED");
    }

    @Test
    void embeddingOutageIsReportedBeforeStreaming() throws Exception {
        embeddingModel.failNext(1);

        mockMvc.perform(member.authorize(
                                post("/api/workspaces/{w}/conversations/{c}/messages", workspaceId, conversationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'question':'怎么回滚','scopeType':'WORKSPACE'}")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(16004));
    }

    @Test
    void feedbackCanBeGivenAndCleared() throws Exception {
        List<Map<String, Object>> events = ask(member, "怎么回滚", "WORKSPACE", null);
        int answerId = (Integer) events.getLast().get("messageId");

        rate(member, answerId, "'UP'").andExpect(status().isOk());
        mockMvc.perform(member.authorize(get("/api/workspaces/{w}/conversations/{c}", workspaceId, conversationId)))
                .andExpect(jsonPath("$.data.messages[1].rating").value("UP"));

        rate(member, answerId, "null").andExpect(status().isOk());
        mockMvc.perform(member.authorize(get("/api/workspaces/{w}/conversations/{c}", workspaceId, conversationId)))
                .andExpect(jsonPath("$.data.messages[1].rating").doesNotExist());
    }

    @Test
    void conversationsArePrivateToTheirCreator() throws Exception {
        List<Map<String, Object>> events = ask(member, "怎么回滚", "WORKSPACE", null);
        int answerId = (Integer) events.getLast().get("messageId");
        LoggedInUser colleague = TestApi.register(mockMvc);
        TestApi.join(mockMvc, workspaceId, member, colleague, "VIEWER");

        mockMvc.perform(colleague.authorize(get("/api/workspaces/{w}/conversations/{c}", workspaceId, conversationId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(16001));
        mockMvc.perform(colleague.authorize(get("/api/workspaces/{w}/conversations", workspaceId)))
                .andExpect(jsonPath("$.data").isEmpty());
        rate(colleague, answerId, "'DOWN'").andExpect(status().isNotFound());
    }

    @Test
    void conversationCanBeDeleted() throws Exception {
        mockMvc.perform(member.authorize(delete("/api/workspaces/{w}/conversations/{c}", workspaceId, conversationId)))
                .andExpect(status().isOk());

        mockMvc.perform(member.authorize(get("/api/workspaces/{w}/conversations", workspaceId)))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private long createConversation(LoggedInUser user) throws Exception {
        String body = mockMvc.perform(user.authorize(post("/api/workspaces/{w}/conversations", workspaceId)))
                .andExpect(jsonPath("$.data.title").value("新会话"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 提问并读取完整的事件流，返回每个事件的 data。 */
    private List<Map<String, Object>> ask(LoggedInUser user, String question, String scopeType, Long scopeNodeId)
            throws Exception {
        MvcResult started = mockMvc.perform(user.authorize(
                                post("/api/workspaces/{w}/conversations/{c}/messages", workspaceId, conversationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(
                                "{'question':'%s','scopeType':'%s','scopeNodeId':%s}",
                                question, scopeType, scopeNodeId)))
                .andExpect(request().asyncStarted())
                .andReturn();
        // SSE 响应不声明字符集，按 UTF-8 读取（浏览器的 TextDecoder 默认同样是 UTF-8）
        String stream = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : stream.split("\n")) {
            if (line.startsWith("data:")) {
                events.add(JsonPath.read(line.substring("data:".length()), "$"));
            }
        }
        return events;
    }

    private ResultActions rate(LoggedInUser user, int messageId, String rating) throws Exception {
        return mockMvc.perform(user.authorize(put("/api/workspaces/{w}/messages/{m}/feedback", workspaceId, messageId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'rating':%s}", rating)));
    }
}
