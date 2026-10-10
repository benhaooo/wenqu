package com.wenqu.auth;

import static com.wenqu.support.TestApi.PASSWORD;
import static com.wenqu.support.TestApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wenqu.support.IntegrationTest;
import com.wenqu.support.MutableClock;
import com.wenqu.support.TestApi;
import com.wenqu.support.TestApi.LoggedInUser;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class AuthFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtEncoder jwtEncoder;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void registerLogsInAndSetsHttpOnlyRefreshCookie() throws Exception {
        String email = TestApi.uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'%s','displayName':'小王'}", email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(header().string(
                                HttpHeaders.SET_COOKIE,
                                allOf(
                                        containsString("wenqu_refresh="),
                                        containsString("HttpOnly"),
                                        containsString("SameSite=Strict"),
                                        containsString("Path=/api/auth"))));
    }

    @Test
    void duplicateEmailIsRejectedIgnoringCase() throws Exception {
        String email = TestApi.uniqueEmail();
        TestApi.register(mockMvc, email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(
                                "{'email':'%s','password':'%s','displayName':'x'}", email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(11001))
                .andExpect(jsonPath("$.message").value("该邮箱已注册"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameMessage() throws Exception {
        LoggedInUser user = TestApi.register(mockMvc);

        for (String credentials : new String[] {
            json("{'email':'%s','password':'wrong-password'}", user.email()),
            json("{'email':'%s','password':'%s'}", TestApi.uniqueEmail(), PASSWORD)
        }) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(credentials))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(11002))
                    .andExpect(jsonPath("$.message").value("邮箱或密码错误"));
        }
    }

    @Test
    void protectedEndpointRequiresValidAccessToken() throws Exception {
        LoggedInUser user = TestApi.register(mockMvc);

        mockMvc.perform(user.authorize(get("/api/auth/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.id()));
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(11003));
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(11003));
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        LoggedInUser user = TestApi.register(mockMvc);
        Instant issuedAt = Instant.now().minus(Duration.ofHours(3));
        String expired = jwtEncoder
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        JwtClaimsSet.builder()
                                .subject(Long.toString(user.id()))
                                .issuedAt(issuedAt)
                                .expiresAt(issuedAt.plus(Duration.ofHours(2)))
                                .build()))
                .getTokenValue();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(11003));
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheWholeFamily() throws Exception {
        LoggedInUser user = TestApi.register(mockMvc);

        LoggedInUser refreshed = TestApi.loggedInUser(refresh(user.refreshCookie()));
        assertThat(refreshed.refreshCookie().getValue())
                .isNotEqualTo(user.refreshCookie().getValue());
        mockMvc.perform(refreshed.authorize(get("/api/auth/me"))).andExpect(status().isOk());

        // 旧 Token 被再次使用：视为泄露，整个 family 作废
        assertThat(refresh(user.refreshCookie()).getResponse().getStatus()).isEqualTo(401);
        MvcResult afterReuse = refresh(refreshed.refreshCookie());
        assertThat(afterReuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(afterReuse.getResponse().getContentAsString()).contains("11004");
    }

    @Test
    void expiredRefreshTokenRequiresLoginAgain() throws Exception {
        LoggedInUser user = TestApi.register(mockMvc);

        clock.advance(Duration.ofDays(7).plusMinutes(1));

        mockMvc.perform(post("/api/auth/refresh").cookie(user.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(11004));
    }

    @Test
    void logoutInvalidatesRefreshTokenAndClearsCookie() throws Exception {
        LoggedInUser user = TestApi.register(mockMvc);

        mockMvc.perform(post("/api/auth/logout").cookie(user.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mockMvc.perform(post("/api/auth/refresh").cookie(user.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(11004));
    }

    private MvcResult refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie)).andReturn();
    }
}
