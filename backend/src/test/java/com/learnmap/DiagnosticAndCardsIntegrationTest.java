package com.learnmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.question.QuestionRepository;
import org.junit.jupiter.api.Test;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiagnosticAndCardsIntegrationTest {

    static final String BOOK = LearningFlowIntegrationTest.BOOK;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QuestionRepository questions;

    @Test
    void threeRoundDiagnosisFindsTheRootCause() throws Exception {
        long learner = user("LEARNER");
        long bookId = upload(learner, null).at("/book/id").asLong();
        long graphId = call(post("/api/books/" + bookId + "/assign"), learner, status().isOk()).get("id").asLong();

        JsonNode s = call(post("/api/graphs/" + graphId + "/diagnostics"), learner, status().isOk());
        long sessionId = s.get("id").asLong();
        assertThat(s.get("round").asInt()).isEqualTo(1);
        assertThat(s.get("totalInRound").asInt()).isEqualTo(3);
        // 再次开始返回同一个未完成的诊断
        assertThat(call(post("/api/graphs/" + graphId + "/diagnostics"), learner, status().isOk()).get("id").asLong())
                .isEqualTo(sessionId);

        // 广度扫描：等式的性质 对，移项 错，解一元一次方程 错
        s = answer(learner, sessionId, s, true);
        s = answer(learner, sessionId, s, false);
        s = answer(learner, sessionId, s, false);

        // 两个错题的前置都测过了，定向诊断没有新题，直接进入验证轮：只验证疑似根因“移项”
        assertThat(s.get("round").asInt()).isEqualTo(3);
        assertThat(s.get("totalInRound").asInt()).isEqualTo(1);
        s = answer(learner, sessionId, s, false);
        assertThat(s.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(s.get("next").isNull()).isTrue();

        JsonNode report = call(get("/api/diagnostics/" + sessionId + "/report"), learner, status().isOk());
        assertThat(report.get("rootCauses")).extracting(n -> n.get("name").asText()).containsExactly("移项");
        assertThat(report.get("rootCauses").get(0).get("impact").asInt()).isEqualTo(1);
        assertThat(report.get("affected")).extracting(n -> n.get("name").asText()).containsExactly("解一元一次方程");
        assertThat(report.get("good")).extracting(n -> n.get("name").asText()).containsExactly("等式的性质");
        assertThat(report.get("untested").asInt()).isZero();

        // 诊断作答已经写进知识画像
        JsonNode graph = call(get("/api/graphs/" + graphId), learner, status().isOk());
        assertThat(graph.get("nodes")).allMatch(n -> !n.get("mastery").isNull());

        long stranger = user("LEARNER");
        call(get("/api/diagnostics/" + sessionId + "/report"), stranger, status().isForbidden());
    }

    @Test
    void cardsAreCreatedPerNodeAndScheduledByFsrs() throws Exception {
        long learner = user("LEARNER");
        long bookId = upload(learner, null).at("/book/id").asLong();
        call(post("/api/books/" + bookId + "/assign"), learner, status().isOk());

        JsonNode due = call(get("/api/cards/due"), learner, status().isOk());
        assertThat(due.get("dueCount").asLong()).isEqualTo(3);
        JsonNode card = due.get("cards").get(0);
        assertThat(card.get("front").asText()).contains("等式的性质");
        assertThat(card.get("back").asText()).contains("等式两边");

        long other = user("LEARNER");
        call(post("/api/cards/" + card.get("id").asLong() + "/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\"}"), other, status().isForbidden());

        JsonNode r = call(post("/api/cards/" + card.get("id").asLong() + "/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":\"GOOD\"}"), learner, status().isOk());
        assertThat(r.get("state").asText()).isEqualTo("REVIEW");
        assertThat(r.get("intervalMinutes").asLong()).isEqualTo(4 * 24 * 60); // 新卡“记得”：稳定性 3.7 天，取整 4 天
        assertThat(LocalDateTime.parse(r.get("nextDueAt").asText())).isAfter(LocalDateTime.now().plusDays(3));
        assertThat(r.get("mastery").asDouble()).isGreaterThan(0);

        JsonNode again = call(post("/api/cards/" + due.get("cards").get(1).get("id").asLong() + "/review")
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"AGAIN\"}"), learner, status().isOk());
        assertThat(again.get("intervalMinutes").asLong()).isEqualTo(10);

        assertThat(call(get("/api/cards/due"), learner, status().isOk()).get("dueCount").asLong()).isEqualTo(1);
    }

    private JsonNode answer(long learner, long sessionId, JsonNode session, boolean correct) throws Exception {
        long itemId = session.get("next").get("id").asLong();
        String answer = correct ? questions.findById(session.at("/next/questionId").asLong()).orElseThrow().getAnswer() : "不知道";
        String body = json.writeValueAsString(Map.of("answer", answer, "selfConfidence", "SURE"));
        return call(post("/api/diagnostics/" + sessionId + "/items/" + itemId + "/answer")
                .contentType(MediaType.APPLICATION_JSON).content(body), learner, status().isOk()).get("session");
    }

    private long user(String role) throws Exception {
        String name = role.toLowerCase() + "-" + UUID.randomUUID();
        String body = json.writeValueAsString(Map.of("username", name, "displayName", name, "role", role));
        return json.readTree(mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode upload(long userId, String reviewMode) throws Exception {
        var req = multipart("/api/books")
                .file(new MockMultipartFile("file", "equation.md", "text/markdown", BOOK.getBytes(StandardCharsets.UTF_8)))
                .param("title", "一元一次方程");
        if (reviewMode != null) {
            req.param("reviewMode", reviewMode);
        }
        return call(req, userId, status().isCreated());
    }

    private JsonNode call(MockHttpServletRequestBuilder req, long userId, ResultMatcher expected) throws Exception {
        String body = mvc.perform(req.header("X-User-Id", userId)).andExpect(expected)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return body.isEmpty() ? null : json.readTree(body);
    }
}
