package com.wenqu.ingestion;

import static com.wenqu.jooq.infra.Tables.OUTBOX_EVENT;
import static com.wenqu.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wenqu.document.service.NodeService;
import com.wenqu.ingestion.service.IngestJobProcessor;
import com.wenqu.retrieval.api.RetrievalApi;
import com.wenqu.retrieval.api.RetrievedChunk;
import com.wenqu.support.FakeEmbeddingModel;
import com.wenqu.support.IntegrationTest;
import com.wenqu.support.MutableClock;
import com.wenqu.support.TestApi;
import com.wenqu.support.TestApi.LoggedInUser;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.awaitility.Awaitility;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class IngestionFlowTest {

    private static final String RUNBOOK = """
            # 部署手册

            发布前确认配置已经合并到主干。

            ## 回滚

            线上出现故障时，先把流量切到旧集群，再执行 kubectl rollout undo 回滚到上一个版本。
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RetrievalApi retrievalApi;

    @Autowired
    NodeService nodeService;

    @Autowired
    IngestJobProcessor processor;

    @Autowired
    FakeEmbeddingModel embeddingModel;

    @Autowired
    MutableClock clock;

    @Autowired
    DSLContext dsl;

    LoggedInUser editor;
    long workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        editor = TestApi.register(mockMvc);
        workspaceId = TestApi.createWorkspace(mockMvc, editor);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
        embeddingModel.reset();
    }

    @Test
    void importedMarkdownBecomesAnIndexedDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "部署手册.md", "application/octet-stream", RUNBOOK.getBytes(StandardCharsets.UTF_8));
        String body = mockMvc.perform(editor.authorize(
                        multipart("/api/workspaces/{w}/imports", workspaceId).file(file)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.fileName").value("部署手册.md"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long documentId = ((Number) JsonPath.read(body, "$.data.documentId")).longValue();

        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/nodes", workspaceId)))
                .andExpect(jsonPath("$.data[0].id").value(documentId))
                .andExpect(jsonPath("$.data[0].title").value("部署手册"));

        Map<String, Object> job = TestApi.awaitImportStatus(mockMvc, workspaceId, editor, documentId, "SUCCEEDED");
        assertThat(job.get("chunkCount")).isEqualTo(job.get("embeddedCount"));

        // 与文件名相同的一级标题作为文档标题，不在正文中重复
        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/documents/{d}", workspaceId, documentId)))
                .andExpect(jsonPath("$.data.content.content[0].type").value("paragraph"))
                .andExpect(jsonPath("$.data.content.content[1].content[0].text").value("回滚"));
        List<RetrievedChunk> hits = retrievalApi.search(workspaceId, Set.of(documentId), "怎么回滚到上一个版本", 3);
        assertThat(hits).first().satisfies(hit -> {
            assertThat(hit.headingPath()).isEqualTo("回滚");
            assertThat(hit.content()).contains("kubectl rollout undo");
            assertThat(hit.blockFrom()).isEqualTo(2);
        });
    }

    @Test
    void originalFileCanBeDownloaded() throws Exception {
        long documentId = TestApi.importAndIndex(mockMvc, workspaceId, editor, "手册.md", RUNBOOK);

        String body = mockMvc.perform(
                        editor.authorize(get("/api/workspaces/{w}/documents/{d}/source-file", workspaceId, documentId)))
                .andExpect(jsonPath("$.data.fileName").value("手册.md"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String url = JsonPath.read(body, "$.data.url");

        HttpResponse<String> download = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(download.body()).isEqualTo(RUNBOOK);
        assertThat(download.headers().firstValue("Content-Disposition"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("attachment"));

        long handwritten = TestApi.createNode(mockMvc, workspaceId, editor, null, "DOCUMENT", "手写");
        mockMvc.perform(editor.authorize(
                        get("/api/workspaces/{w}/documents/{d}/source-file", workspaceId, handwritten)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(14004));
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "logo.md", "text/markdown", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        mockMvc.perform(editor.authorize(
                        multipart("/api/workspaces/{w}/imports", workspaceId).file(file)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(14001));
        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/nodes", workspaceId)))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void scannedPdfFailsWithoutRetry() throws Exception {
        byte[] blankPdf;
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            blankPdf = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "合同.pdf", "application/pdf", blankPdf);
        String body = mockMvc.perform(editor.authorize(
                        multipart("/api/workspaces/{w}/imports", workspaceId).file(file)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long documentId = ((Number) JsonPath.read(body, "$.data.documentId")).longValue();

        Map<String, Object> job = TestApi.awaitImportStatus(mockMvc, workspaceId, editor, documentId, "FAILED");

        assertThat(job.get("errorMessage")).isEqualTo("未识别到文字，可能是扫描件");
        assertThat(job.get("attempts")).isEqualTo(1);
        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/index-status", workspaceId)))
                .andExpect(jsonPath("$.data[0].documentId").value(documentId))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"));
    }

    @Test
    void modelOutageIsRetriedAfterADelay() throws Exception {
        embeddingModel.failNext(1);

        long documentId = TestApi.importFile(mockMvc, workspaceId, editor, "手册.md", RUNBOOK);

        Map<String, Object> waiting =
                TestApi.awaitImport(mockMvc, workspaceId, editor, documentId, job -> job.get("errorMessage") != null);
        assertThat(waiting.get("status")).isEqualTo("QUEUED");
        assertThat(waiting.get("attempts")).isEqualTo(1);
        Map<String, Object> done = TestApi.awaitImportStatus(mockMvc, workspaceId, editor, documentId, "SUCCEEDED");
        assertThat(done.get("attempts")).isEqualTo(2);
        assertThat(done.get("errorMessage")).isNull();
    }

    @Test
    void jobFailsWhenRetriesAreExhaustedAndCanBeRetriedManually() throws Exception {
        embeddingModel.failNext(1_000);
        long documentId = TestApi.importFile(mockMvc, workspaceId, editor, "手册.md", RUNBOOK);
        long jobId = ((Number) TestApi.awaitImportStatus(mockMvc, workspaceId, editor, documentId, "QUEUED")
                        .get("id"))
                .longValue();

        processor.process(jobId, true);

        Map<String, Object> failed = TestApi.awaitImportStatus(mockMvc, workspaceId, editor, documentId, "FAILED");
        assertThat((String) failed.get("errorMessage")).startsWith("处理出错");

        embeddingModel.reset();
        mockMvc.perform(editor.authorize(post("/api/workspaces/{w}/ingest-jobs/{j}/retry", workspaceId, jobId)))
                .andExpect(status().isOk());
        TestApi.awaitImportStatus(mockMvc, workspaceId, editor, documentId, "SUCCEEDED");
        mockMvc.perform(editor.authorize(post("/api/workspaces/{w}/ingest-jobs/{j}/retry", workspaceId, jobId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(14003));
    }

    @Test
    void editsAreReindexedThirtySecondsAfterTheLastSave() throws Exception {
        long documentId = TestApi.createNode(mockMvc, workspaceId, editor, null, "DOCUMENT", "缓存设计");

        save(documentId, "缓存失效采用主动删除。", 0);
        save(documentId, "缓存失效采用主动删除，并设置五分钟过期时间。", 1);

        assertThat(dsl.fetchCount(
                        OUTBOX_EVENT,
                        OUTBOX_EVENT
                                .DEDUP_KEY
                                .eq("document.content-changed:" + documentId)
                                .and(OUTBOX_EVENT.PUBLISHED_AT.isNull())))
                .isEqualTo(1);
        Thread.sleep(1_000);
        assertThat(retrievalApi.indexedVersion(documentId)).isEmpty();

        clock.advance(Duration.ofSeconds(31));

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> retrievalApi.indexedVersion(documentId).orElse(0) == 2);
        assertThat(retrievalApi.search(workspaceId, Set.of(documentId), "缓存过期时间", 1))
                .singleElement()
                .extracting(RetrievedChunk::content)
                .isEqualTo("缓存失效采用主动删除，并设置五分钟过期时间。");
    }

    @Test
    void purgedDocumentsLoseTheirChunksAndSourceFiles() throws Exception {
        long documentId = TestApi.importAndIndex(mockMvc, workspaceId, editor, "手册.md", RUNBOOK);
        mockMvc.perform(editor.authorize(delete("/api/workspaces/{w}/nodes/{n}", workspaceId, documentId)))
                .andExpect(status().isOk());
        assertThat(retrievalApi.indexedVersion(documentId)).isPresent();

        clock.advance(Duration.ofDays(31));
        nodeService.purgeExpiredTrash();

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(() -> retrievalApi.indexedVersion(documentId).isEmpty());
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/imports", workspaceId)))
                        .andExpect(jsonPath("$.data").isEmpty()));
    }

    @Test
    void adminCanReindexTheWholeWorkspace() throws Exception {
        TestApi.importAndIndex(mockMvc, workspaceId, editor, "手册.md", RUNBOOK);
        TestApi.createNode(mockMvc, workspaceId, editor, null, "DOCUMENT", "空文档");

        mockMvc.perform(editor.authorize(post("/api/workspaces/{w}/reindex", workspaceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queued").value(2));
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                        () -> mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/index-status", workspaceId)))
                                .andExpect(jsonPath("$.data").isEmpty()));
    }

    private void save(long documentId, String text, int baseVersion) throws Exception {
        mockMvc.perform(editor.authorize(put("/api/workspaces/{w}/documents/{d}/content", workspaceId, documentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(
                                "{'baseVersion':%d,'content':{'type':'doc','content':[{'type':'paragraph','content':[{'type':'text','text':'%s'}]}]}}",
                                baseVersion, text)))
                .andExpect(status().isOk());
    }
}
