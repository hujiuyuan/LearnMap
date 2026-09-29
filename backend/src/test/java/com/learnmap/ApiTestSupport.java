package com.learnmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.question.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 接口级集成测试的公用工具：建用户、传书、分配图谱、调接口 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    /** 三小节的一元一次方程示例书 */
    protected static final String BOOK = LearningFlowIntegrationTest.BOOK;

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected QuestionRepository questions;

    protected long user(String role) throws Exception {
        String name = role.toLowerCase() + "-" + UUID.randomUUID();
        String body = json.writeValueAsString(Map.of("username", name, "displayName", name, "role", role));
        return json.readTree(mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    protected JsonNode upload(long userId, String text) throws Exception {
        return upload(userId, text, null);
    }

    protected JsonNode upload(long userId, String text, String reviewMode) throws Exception {
        var req = multipart("/api/books")
                .file(new MockMultipartFile("file", "equation.md", "text/markdown", text.getBytes(StandardCharsets.UTF_8)))
                .param("title", "一元一次方程");
        if (reviewMode != null) {
            req.param("reviewMode", reviewMode);
        }
        return call(req, userId, status().isCreated());
    }

    /** 学习者自己传一本书并分给自己，返回图谱副本 id */
    protected long assignedGraph(long learner) throws Exception {
        long bookId = upload(learner, BOOK).at("/book/id").asLong();
        return call(post("/api/books/" + bookId + "/assign"), learner, status().isOk()).get("id").asLong();
    }

    protected Map<String, Long> nodeIds(long viewer, long graphId) throws Exception {
        Map<String, Long> out = new HashMap<>();
        call(get("/api/graphs/" + graphId), viewer, status().isOk()).get("nodes")
                .forEach(n -> out.put(n.get("name").asText(), n.get("id").asLong()));
        return out;
    }

    protected String rightAnswer(long questionId) {
        return questions.findById(questionId).orElseThrow().getAnswer();
    }

    protected JsonNode postJson(String url, Object body, long userId, ResultMatcher expected) throws Exception {
        return call(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), userId, expected);
    }

    protected JsonNode call(MockHttpServletRequestBuilder req, long userId, ResultMatcher expected) throws Exception {
        String body = mvc.perform(req.header("X-User-Id", userId)).andExpect(expected)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isEmpty() ? null : json.readTree(body);
    }
}
