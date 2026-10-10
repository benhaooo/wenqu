package com.wenqu.workspace;

import static com.wenqu.support.TestApi.json;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wenqu.support.IntegrationTest;
import com.wenqu.support.MutableClock;
import com.wenqu.support.TestApi;
import com.wenqu.support.TestApi.LoggedInUser;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class WorkspaceFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    LoggedInUser admin;
    LoggedInUser invitee;
    long workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestApi.register(mockMvc);
        invitee = TestApi.register(mockMvc);
        workspaceId = TestApi.createWorkspace(mockMvc, admin);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void creatorBecomesAdminAndSeesWorkspaceInList() throws Exception {
        mockMvc.perform(admin.authorize(get("/api/workspaces")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(workspaceId))
                .andExpect(jsonPath("$.data[0].myRole").value("ADMIN"));
    }

    @Test
    void inviteeJoinsWithInvitedRole() throws Exception {
        String token = TestApi.createInvitation(mockMvc, workspaceId, admin, "EDITOR");

        mockMvc.perform(invitee.authorize(get("/api/invitations/{token}", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceName").value("测试空间"))
                .andExpect(jsonPath("$.data.role").value("EDITOR"))
                .andExpect(jsonPath("$.data.alreadyMember").value(false));
        mockMvc.perform(invitee.authorize(post("/api/invitations/{token}/accept", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workspaceId))
                .andExpect(jsonPath("$.data.myRole").value("EDITOR"));

        mockMvc.perform(admin.authorize(get("/api/workspaces/{id}/members", workspaceId)))
                .andExpect(jsonPath("$.data[*].userId", containsInAnyOrder((int) admin.id(), (int) invitee.id())));
    }

    @Test
    void acceptingAgainKeepsExistingRole() throws Exception {
        TestApi.join(mockMvc, workspaceId, admin, invitee, "VIEWER");
        String adminInvitation = TestApi.createInvitation(mockMvc, workspaceId, admin, "ADMIN");

        mockMvc.perform(invitee.authorize(post("/api/invitations/{token}/accept", adminInvitation)))
                .andExpect(jsonPath("$.data.myRole").value("VIEWER"));
    }

    @Test
    void revokedInvitationCannotBeUsed() throws Exception {
        String token = TestApi.createInvitation(mockMvc, workspaceId, admin, "EDITOR");
        String body = mockMvc.perform(admin.authorize(get("/api/workspaces/{id}/invitations", workspaceId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long invitationId = ((Number) JsonPath.read(body, "$.data[0].id")).longValue();

        mockMvc.perform(admin.authorize(
                        delete("/api/workspaces/{id}/invitations/{invitationId}", workspaceId, invitationId)))
                .andExpect(status().isOk());

        mockMvc.perform(invitee.authorize(post("/api/invitations/{token}/accept", token)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(12012));
        mockMvc.perform(admin.authorize(get("/api/workspaces/{id}/invitations", workspaceId)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void expiredInvitationCannotBeUsed() throws Exception {
        String token = TestApi.createInvitation(mockMvc, workspaceId, admin, "EDITOR");

        clock.advance(Duration.ofDays(7).plusMinutes(1));

        mockMvc.perform(invitee.authorize(post("/api/invitations/{token}/accept", token)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(12011));
        mockMvc.perform(invitee.authorize(get("/api/workspaces/{id}", workspaceId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownInvitationIsNotFound() throws Exception {
        mockMvc.perform(invitee.authorize(post("/api/invitations/{token}/accept", "no-such-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(12010));
    }

    @Test
    void invitationValidityIsLimitedToThirtyDays() throws Exception {
        mockMvc.perform(admin.authorize(post("/api/workspaces/{id}/invitations", workspaceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'role':'EDITOR','validDays':31}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001));
    }

    @Test
    void viewerCannotRenameWorkspace() throws Exception {
        TestApi.join(mockMvc, workspaceId, admin, invitee, "VIEWER");

        mockMvc.perform(invitee.authorize(patch("/api/workspaces/{id}", workspaceId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'改名'}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(12003));
    }
}
