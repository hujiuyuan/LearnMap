package com.learnmap.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 生产模式（不认 X-User-Id）下的注册、登录、令牌、注销 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "learnmap.auth.allow-user-id-header=false")
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void registerLoginAndUseToken() throws Exception {
        String name = "u-" + UUID.randomUUID();
        JsonNode reg = send(post("/api/auth/register"), Map.of("username", name, "password", "secret1", "role", "LEARNER"),
                null, status().isCreated());
        String token = reg.get("token").asText();
        long id = reg.at("/user/id").asLong();

        JsonNode me = send(get("/api/me"), null, token, status().isOk());
        assertThat(me.get("id").asLong()).isEqualTo(id);

        send(post("/api/auth/login"), Map.of("username", name, "password", "wrong-pw"), null, status().isBadRequest());
        String token2 = send(post("/api/auth/login"), Map.of("username", name, "password", "secret1"), null, status().isOk())
                .get("token").asText();
        assertThat(token2).isNotEqualTo(token);

        send(post("/api/auth/logout"), null, token, status().isNoContent());
        send(get("/api/me"), null, token, status().isUnauthorized());
        send(get("/api/me"), null, token2, status().isOk());
    }

    @Test
    void userIdHeaderIsIgnoredWithoutToken() throws Exception {
        String name = "u-" + UUID.randomUUID();
        long id = send(post("/api/auth/register"), Map.of("username", name, "password", "secret1"), null, status().isCreated())
                .at("/user/id").asLong();
        mvc.perform(get("/api/me").header("X-User-Id", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void cannotSelfRegisterOpsNorCreateUsersDirectly() throws Exception {
        send(post("/api/auth/register"), Map.of("username", "o-" + UUID.randomUUID(), "password", "secret1", "role", "OPS"),
                null, status().isForbidden());
        send(post("/api/auth/register"), Map.of("username", "s-" + UUID.randomUUID(), "password", "123"), null, status().isBadRequest());

        String learnerToken = send(post("/api/auth/register"), Map.of("username", "l-" + UUID.randomUUID(), "password", "secret1"),
                null, status().isCreated()).get("token").asText();
        Map<String, String> newUser = Map.of("username", "x-" + UUID.randomUUID(), "displayName", "x", "role", "OPS");
        send(post("/api/users"), newUser, null, status().isForbidden());
        send(post("/api/users"), newUser, learnerToken, status().isForbidden());
    }

    @Test
    void duplicateUsernameRejected() throws Exception {
        String name = "d-" + UUID.randomUUID();
        send(post("/api/auth/register"), Map.of("username", name, "password", "secret1"), null, status().isCreated());
        send(post("/api/auth/register"), Map.of("username", name, "password", "secret2"), null, status().isBadRequest());
    }

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, String token, ResultMatcher expected) throws Exception {
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        String out = mvc.perform(req).andExpect(expected).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return out.isEmpty() ? null : json.readTree(out);
    }
}
