package com.wenqu.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.wenqu.support.IntegrationTest;
import com.wenqu.support.TestApi;
import com.wenqu.support.TestApi.LoggedInUser;
import com.wenqu.workspace.api.RequireWorkspaceRole;
import com.wenqu.workspace.api.WorkspaceRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 自动覆盖所有空间内接口（路径含 {workspaceId}）：非成员一律 404，角色低于要求一律 403。
 *
 * <p>直接调用接口验证，而不是看前端按钮。新增的空间内接口无需修改本测试即被覆盖。
 */
@IntegrationTest
class WorkspaceAccessCoverageTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    Map<WorkspaceRole, LoggedInUser> members;
    LoggedInUser outsider;
    long workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        LoggedInUser admin = TestApi.register(mockMvc);
        LoggedInUser editor = TestApi.register(mockMvc);
        LoggedInUser viewer = TestApi.register(mockMvc);
        outsider = TestApi.register(mockMvc);
        workspaceId = TestApi.createWorkspace(mockMvc, admin);
        TestApi.join(mockMvc, workspaceId, admin, editor, "EDITOR");
        TestApi.join(mockMvc, workspaceId, admin, viewer, "VIEWER");
        members = Map.of(WorkspaceRole.ADMIN, admin, WorkspaceRole.EDITOR, editor, WorkspaceRole.VIEWER, viewer);
    }

    @Test
    void everyWorkspaceEndpointDeclaresItsRequiredRole() {
        assertThat(workspaceEndpoints())
                .isNotEmpty()
                .allSatisfy(endpoint -> assertThat(endpoint.requiredRole())
                        .as("%s 缺少 @RequireWorkspaceRole", endpoint)
                        .isNotNull());
    }

    @Test
    void outsiderGetsNotFoundOnEveryWorkspaceEndpoint() throws Exception {
        for (Endpoint endpoint : workspaceEndpoints()) {
            MvcResult result = call(endpoint, outsider);
            assertThat(result.getResponse().getStatus()).as(endpoint.toString()).isEqualTo(404);
            assertThat(result.getResponse().getContentAsString())
                    .as(endpoint.toString())
                    .contains("\"code\":12004");
        }
    }

    @Test
    void membersBelowRequiredRoleGetForbidden() throws Exception {
        for (Endpoint endpoint : workspaceEndpoints()) {
            for (WorkspaceRole role : WorkspaceRole.values()) {
                MvcResult result = call(endpoint, members.get(role));
                String body = result.getResponse().getContentAsString();
                if (role.includes(endpoint.requiredRole())) {
                    assertThat(body).as("%s as %s", endpoint, role).doesNotContain("\"code\":12003", "\"code\":12004");
                } else {
                    assertThat(result.getResponse().getStatus())
                            .as("%s as %s", endpoint, role)
                            .isEqualTo(403);
                    assertThat(body).as("%s as %s", endpoint, role).contains("\"code\":12003");
                }
            }
        }
    }

    private MvcResult call(Endpoint endpoint, LoggedInUser user) throws Exception {
        String path = endpoint.pattern()
                .replace("{workspaceId}", Long.toString(workspaceId))
                .replaceAll("\\{[^}]+}", "1");
        if (endpoint.multipart()) {
            return mockMvc.perform(user.authorize(
                            multipart(endpoint.method(), path).file(new MockMultipartFile("file", new byte[0]))))
                    .andReturn();
        }
        return mockMvc.perform(user.authorize(request(endpoint.method(), path))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
    }

    private List<Endpoint> workspaceEndpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            RequireWorkspaceRole requirement = entry.getValue().getMethodAnnotation(RequireWorkspaceRole.class);
            for (String pattern : entry.getKey().getPatternValues()) {
                if (!pattern.contains("{workspaceId}")) {
                    continue;
                }
                boolean multipart = entry.getKey().getConsumesCondition().getConsumableMediaTypes().stream()
                        .anyMatch(MediaType.MULTIPART_FORM_DATA::includes);
                for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                    endpoints.add(new Endpoint(
                            HttpMethod.valueOf(method.name()),
                            pattern,
                            requirement == null ? null : requirement.value(),
                            multipart));
                }
            }
        }
        return endpoints;
    }

    record Endpoint(HttpMethod method, String pattern, WorkspaceRole requiredRole, boolean multipart) {

        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }
}
