package com.wenqu.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    /** 用单引号书写 JSON，提高测试可读性。 */
    public static String json(String template, Object... args) {
        return template.formatted(args).replace('\'', '"');
    }
}
