package com.wenqu.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/** 集成测试中通过真实接口准备数据。 */
public final class TestApi {

    public static final String PASSWORD = "password123";

    private TestApi() {}

    public record LoggedInUser(long id, String email, String accessToken, Cookie refreshCookie) {

        public <B extends AbstractMockHttpServletRequestBuilder<B>> B authorize(B request) {
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    public static LoggedInUser register(MockMvc mockMvc) throws Exception {
        return register(mockMvc, uniqueEmail());
    }

    public static LoggedInUser register(MockMvc mockMvc, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'%s','displayName':'测试用户'}", email, PASSWORD)))
                .andReturn();
        return loggedInUser(result);
    }

    public static LoggedInUser loggedInUser(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("登录失败：" + body);
        }
        return new LoggedInUser(
                ((Number) JsonPath.read(body, "$.data.user.id")).longValue(),
                JsonPath.read(body, "$.data.user.email"),
                JsonPath.read(body, "$.data.accessToken"),
                result.getResponse().getCookie("wenqu_refresh"));
    }

    public static long createWorkspace(MockMvc mockMvc, LoggedInUser owner) throws Exception {
        String body = mockMvc.perform(owner.authorize(post("/api/workspaces"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'测试空间'}")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 由管理员创建指定角色的邀请，member 接受后加入空间。 */
    public static void join(MockMvc mockMvc, long workspaceId, LoggedInUser admin, LoggedInUser member, String role)
            throws Exception {
        String token = createInvitation(mockMvc, workspaceId, admin, role);
        mockMvc.perform(member.authorize(post("/api/invitations/{token}/accept", token)));
    }

    public static String createInvitation(MockMvc mockMvc, long workspaceId, LoggedInUser admin, String role)
            throws Exception {
        String body = mockMvc.perform(admin.authorize(post("/api/workspaces/{id}/invitations", workspaceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'role':'%s','validDays':7}", role)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.data.token");
    }

    /** 新建节点并返回 id；parentId 为 null 表示根目录。 */
    public static long createNode(
            MockMvc mockMvc, long workspaceId, LoggedInUser user, Long parentId, String type, String title)
            throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        String body = mockMvc.perform(user.authorize(post("/api/workspaces/{id}/nodes", workspaceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'parentId':%s,'type':'%s','title':'%s'}", parent, type, title)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /** 导入文本文件（Markdown 或纯文本），返回新文档 id，不等待索引完成。 */
    public static long importFile(MockMvc mockMvc, long workspaceId, LoggedInUser user, String fileName, String content)
            throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", fileName, "text/plain", content.getBytes(StandardCharsets.UTF_8));
        String body = mockMvc.perform(user.authorize(
                        multipart("/api/workspaces/{id}/imports", workspaceId).file(file)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.documentId")).longValue();
    }

    /** 导入文件并等待解析和索引完成，返回文档 id。 */
    public static long importAndIndex(
            MockMvc mockMvc, long workspaceId, LoggedInUser user, String fileName, String content) throws Exception {
        long documentId = importFile(mockMvc, workspaceId, user, fileName, content);
        awaitImportStatus(mockMvc, workspaceId, user, documentId, "SUCCEEDED");
        return documentId;
    }

    /** 等待文档最近的导入任务进入指定状态（最多 30 秒），返回该任务的 JSON。 */
    public static Map<String, Object> awaitImportStatus(
            MockMvc mockMvc, long workspaceId, LoggedInUser user, long documentId, String status) {
        return awaitImport(mockMvc, workspaceId, user, documentId, job -> status.equals(job.get("status")));
    }

    /** 等待文档最近的导入任务满足条件（最多 30 秒），返回该任务的 JSON。 */
    public static Map<String, Object> awaitImport(
            MockMvc mockMvc,
            long workspaceId,
            LoggedInUser user,
            long documentId,
            Predicate<Map<String, Object>> condition) {
        AtomicReference<Map<String, Object>> job = new AtomicReference<>();
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> {
                    String body = mockMvc.perform(user.authorize(get("/api/workspaces/{id}/imports", workspaceId)))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    List<Map<String, Object>> jobs =
                            JsonPath.read(body, "$.data[?(@.documentId == %d)]".formatted(documentId));
                    if (jobs.isEmpty()) {
                        return false;
                    }
                    job.set(jobs.getFirst());
                    return condition.test(jobs.getFirst());
                });
        return job.get();
    }

    /** 用单引号书写 JSON，提高测试可读性。 */
    public static String json(String template, Object... args) {
        return template.formatted(args).replace('\'', '"');
    }
}
