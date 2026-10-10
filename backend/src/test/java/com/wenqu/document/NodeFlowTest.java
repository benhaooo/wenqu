package com.wenqu.document;

import static com.wenqu.jooq.document.Tables.CONTENT;
import static com.wenqu.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wenqu.document.service.NodeService;
import com.wenqu.support.IntegrationTest;
import com.wenqu.support.MutableClock;
import com.wenqu.support.TestApi;
import com.wenqu.support.TestApi.LoggedInUser;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class NodeFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    NodeService nodeService;

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
    void resetClock() {
        clock.reset();
    }

    @Test
    void newNodesAreAppendedInOrder() throws Exception {
        long a = node(null, "DOCUMENT", "A");
        long b = node(null, "DOCUMENT", "B");
        long c = node(null, "DOCUMENT", "C");

        assertThat(childrenOf(null)).containsExactly(a, b, c);
    }

    @Test
    void reorderAndCrossFolderMovePersist() throws Exception {
        long folder = node(null, "FOLDER", "文件夹");
        long a = node(null, "DOCUMENT", "A");
        long b = node(null, "DOCUMENT", "B");
        long c = node(folder, "DOCUMENT", "C");

        move(b, null, null).andExpect(status().isOk());
        assertThat(childrenOf(null)).containsExactly(b, folder, a);

        move(a, folder, c).andExpect(status().isOk());
        assertThat(childrenOf(folder)).containsExactly(c, a);

        move(a, folder, null).andExpect(status().isOk());
        assertThat(childrenOf(folder)).containsExactly(a, c);
        assertThat(childrenOf(null)).containsExactly(b, folder);
    }

    @Test
    void folderCannotMoveIntoItselfOrItsDescendant() throws Exception {
        long parent = node(null, "FOLDER", "父");
        long child = node(parent, "FOLDER", "子");
        long grandchild = node(child, "FOLDER", "孙");

        for (long target : new long[] {parent, child, grandchild}) {
            move(parent, target, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(13003));
        }
    }

    @Test
    void concurrentOppositeMovesNeverCreateACycle() throws Exception {
        long a = node(null, "FOLDER", "A");
        long b = node(null, "FOLDER", "B");

        var executor = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> moves = List.of(
                () -> move(a, b, null).andReturn().getResponse().getStatus(),
                () -> move(b, a, null).andReturn().getResponse().getStatus());
        List<Integer> statuses =
                executor.invokeAll(moves).stream().map(NodeFlowTest::await).toList();
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
    }

    @Test
    void documentCannotBeAParent() throws Exception {
        long document = node(null, "DOCUMENT", "文档");

        create(document, "DOCUMENT", "子文档")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(13002));
    }

    @Test
    void deletingFolderMovesSubtreeToTrashAndRestoreBringsItBack() throws Exception {
        long folder = node(null, "FOLDER", "文件夹");
        long child = node(folder, "FOLDER", "子文件夹");
        long leaf = node(child, "DOCUMENT", "文档");

        mockMvc.perform(editor.authorize(delete("/api/workspaces/{w}/nodes/{n}", workspaceId, folder)))
                .andExpect(status().isOk());
        assertThat(allNodes()).doesNotContain(folder, child, leaf);
        mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/trash", workspaceId)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(folder))
                .andExpect(jsonPath("$.data[0].descendantCount").value(2));

        restore(folder).andExpect(status().isOk());
        assertThat(childrenOf(folder)).containsExactly(child);
        assertThat(childrenOf(child)).containsExactly(leaf);
    }

    @Test
    void restoringWhenParentIsGoneMovesToRoot() throws Exception {
        long folder = node(null, "FOLDER", "文件夹");
        long document = node(folder, "DOCUMENT", "文档");
        mockMvc.perform(editor.authorize(delete("/api/workspaces/{w}/nodes/{n}", workspaceId, document)));
        mockMvc.perform(editor.authorize(delete("/api/workspaces/{w}/nodes/{n}", workspaceId, folder)));

        restore(document).andExpect(status().isOk());

        assertThat(childrenOf(null)).containsExactly(document);
    }

    @Test
    void trashIsPurgedAfterThirtyDays() throws Exception {
        long document = node(null, "DOCUMENT", "文档");
        mockMvc.perform(editor.authorize(delete("/api/workspaces/{w}/nodes/{n}", workspaceId, document)));

        clock.advance(Duration.ofDays(29));
        nodeService.purgeExpiredTrash();
        restore(document).andExpect(status().isOk());
        mockMvc.perform(editor.authorize(delete("/api/workspaces/{w}/nodes/{n}", workspaceId, document)));

        clock.advance(Duration.ofDays(31));
        nodeService.purgeExpiredTrash();
        restore(document)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(13005));
    }

    @Test
    void searchMatchesTitleAndBodyAndEscapesWildcards() throws Exception {
        long manual = node(null, "DOCUMENT", "部署手册");
        long notes = node(null, "DOCUMENT", "周会记录");
        dsl.update(CONTENT)
                .set(CONTENT.PLAIN_TEXT, "本周讨论了部署流程的回滚策略，结论是 100% 灰度")
                .where(CONTENT.NODE_ID.eq(notes))
                .execute();

        mockMvc.perform(editor.authorize(
                        get("/api/workspaces/{w}/search", workspaceId).param("q", "部署")))
                .andExpect(jsonPath("$.data[0].id").value(manual))
                .andExpect(jsonPath("$.data[1].id").value(notes))
                .andExpect(jsonPath("$.data[1].snippet").value("本周讨论了部署流程的回滚策略，结论是 100% 灰度"));
        mockMvc.perform(editor.authorize(
                        get("/api/workspaces/{w}/search", workspaceId).param("q", "_")))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private long node(Long parentId, String type, String title) throws Exception {
        return TestApi.createNode(mockMvc, workspaceId, editor, parentId, type, title);
    }

    private ResultActions create(Long parentId, String type, String title) throws Exception {
        return mockMvc.perform(editor.authorize(post("/api/workspaces/{w}/nodes", workspaceId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'parentId':%s,'type':'%s','title':'%s'}", parentId, type, title)));
    }

    private ResultActions move(long nodeId, Long parentId, Long afterNodeId) throws Exception {
        return mockMvc.perform(editor.authorize(post("/api/workspaces/{w}/nodes/{n}/move", workspaceId, nodeId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{'parentId':%s,'afterNodeId':%s}", parentId, afterNodeId)));
    }

    private ResultActions restore(long nodeId) throws Exception {
        return mockMvc.perform(editor.authorize(post("/api/workspaces/{w}/trash/{n}/restore", workspaceId, nodeId)));
    }

    /** 按接口返回的 position 排序后的子节点 id，相当于刷新页面后看到的顺序。 */
    private List<Long> childrenOf(Long parentId) throws Exception {
        String body = mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/nodes", workspaceId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String filter =
                parentId == null ? "$.data[?(@.parentId == null)]" : "$.data[?(@.parentId == " + parentId + ")]";
        List<Map<String, Object>> nodes = JsonPath.read(body, filter);
        return nodes.stream()
                .sorted(Comparator.comparing(node -> (String) node.get("position")))
                .map(node -> ((Number) node.get("id")).longValue())
                .toList();
    }

    private List<Long> allNodes() throws Exception {
        String body = mockMvc.perform(editor.authorize(get("/api/workspaces/{w}/nodes", workspaceId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.data[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
