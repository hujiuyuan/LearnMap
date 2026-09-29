package com.learnmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.question.QuestionRepository;
import com.learnmap.retest.RetestReason;
import com.learnmap.retest.ScheduledRetest;
import com.learnmap.retest.ScheduledRetestRepository;
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
import java.util.HashMap;
import java.util.List;
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
class LearningModesIntegrationTest {

    static final String BOOK = LearningFlowIntegrationTest.BOOK;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired QuestionRepository questions;
    @Autowired ScheduledRetestRepository retests;

    @Test
    void deliberatePracticeVerifiesPractisesAndSchedulesRecheck() throws Exception {
        long learner = user("LEARNER");
        long graphId = assignedGraph(learner);
        Map<String, Long> nodes = nodeIds(learner, graphId);

        JsonNode s = start(learner, graphId, "DELIBERATE", nodes.get("移项"));
        assertThat(s.get("phase").asText()).isEqualTo("VERIFY");
        assertThat(s.get("batchSize").asInt()).isEqualTo(3);
        s = answer(learner, s, true);
        s = answer(learner, s, false);
        s = answer(learner, s, true);

        // 没有全对 → 专项练习：移项 3 道 + 掌握度低的前置“等式的性质” 1 道
        assertThat(s.get("phase").asText()).isEqualTo("PRACTICE");
        assertThat(s.get("batchSize").asInt()).isEqualTo(4);
        while (!s.get("next").isNull()) {
            s = answer(learner, s, true);
        }
        assertThat(s.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(s.get("summary").asText()).contains("做对 4 / 4", "3 天后");

        // 3 天后复验：把时间拨到期后，复测会出现在到期列表里
        List<ScheduledRetest> scheduled = retests.findByLearnerIdAndDoneFalseOrderByDueAtAsc(learner);
        assertThat(scheduled).extracting(ScheduledRetest::getReason).containsExactly(RetestReason.DELIBERATE_RECHECK);
        assertThat(call(get("/api/retests/due"), learner, status().isOk())).isEmpty();
        ScheduledRetest r = scheduled.get(0);
        r.setDueAt(LocalDateTime.now().minusMinutes(1));
        retests.save(r);
        JsonNode due = call(get("/api/retests/due"), learner, status().isOk());
        assertThat(due).hasSize(1);
        String right = questions.findById(due.get(0).get("questionId").asLong()).orElseThrow().getAnswer();
        JsonNode result = call(post("/api/retests/" + r.getId() + "/answer").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("answer", right))), learner, status().isOk());
        assertThat(result.get("updates").get(0).get("nodeName").asText()).isEqualTo("移项");
        assertThat(call(get("/api/retests/due"), learner, status().isOk())).isEmpty();
    }

    @Test
    void firstPrinciplesDigsDownToTheFoundationAndBuildsBackUp() throws Exception {
        long learner = user("LEARNER");
        long graphId = assignedGraph(learner);
        Map<String, Long> nodes = nodeIds(learner, graphId);

        JsonNode s = start(learner, graphId, "FIRST_PRINCIPLES", nodes.get("解一元一次方程"));
        // 目标数据不足 → 探测目标：不会
        assertThat(s.get("phase").asText()).isEqualTo("PROBE");
        s = answer(learner, s, false);
        s = answer(learner, s, false);
        // 往下拆到“移项”：不会
        assertThat(s.get("phase").asText()).isEqualTo("PROBE");
        s = answer(learner, s, false);
        s = answer(learner, s, false);
        // 再往下到“等式的性质”：会 → 这就是地基
        s = answer(learner, s, true);
        s = answer(learner, s, true);

        // 从下往上补：先移项（带讲解），再目标本身
        assertThat(s.get("phase").asText()).isEqualTo("LEARN");
        assertThat(s.at("/lesson/name").asText()).isEqualTo("移项");
        assertThat(s.at("/lesson/definition").asText()).contains("改变符号");
        for (int i = 0; i < 3; i++) {
            s = answer(learner, s, true);
        }
        assertThat(s.at("/lesson/name").asText()).isEqualTo("解一元一次方程");
        for (int i = 0; i < 3; i++) {
            s = answer(learner, s, true);
        }
        assertThat(s.get("phase").asText()).isEqualTo("GOAL");
        s = answer(learner, s, true);
        s = answer(learner, s, true);
        assertThat(s.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(s.get("summary").asText()).contains("全部做对", "补了 2 个知识点", "最深 1 层");

        // 补会的节点安排了 3 / 7 / 21 天的延时复测
        assertThat(retests.findByLearnerIdAndDoneFalseOrderByDueAtAsc(learner)).extracting(ScheduledRetest::getReason)
                .containsOnly(RetestReason.RETENTION_3D, RetestReason.RETENTION_7D, RetestReason.RETENTION_21D)
                .hasSize(6);
    }

    @Test
    void interleavedPracticeNeedsLearnedNodesThenMixesThem() throws Exception {
        long learner = user("LEARNER");
        long graphId = assignedGraph(learner);
        String body = json.writeValueAsString(Map.of("mode", "INTERLEAVED"));
        call(post("/api/graphs/" + graphId + "/practice").contentType(MediaType.APPLICATION_JSON).content(body),
                learner, status().isBadRequest());

        // 每个节点做对一道题，掌握度过 50%
        JsonNode graph = call(get("/api/graphs/" + graphId), learner, status().isOk());
        for (JsonNode n : graph.get("nodes")) {
            JsonNode q = call(get("/api/graphs/" + graphId + "/nodes/" + n.get("id").asLong() + "/questions"), learner, status().isOk()).get(0);
            String right = questions.findById(q.get("id").asLong()).orElseThrow().getAnswer();
            call(post("/api/answers").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("questionId", q.get("id").asLong(), "answer", right))), learner, status().isOk());
        }

        JsonNode s = call(post("/api/graphs/" + graphId + "/practice").contentType(MediaType.APPLICATION_JSON).content(body),
                learner, status().isOk());
        assertThat(s.get("phase").asText()).isEqualTo("MIXED");
        assertThat(s.get("batchSize").asInt()).isEqualTo(8);
        int i = 0;
        while (!s.get("next").isNull()) {
            s = answer(learner, s, i++ % 4 != 0, "WRONG_METHOD");
        }
        assertThat(s.get("summary").asText()).contains("做对 6 / 8", "2 道是方法选错");
    }

    @Test
    void feynmanExplanationIsGradedAndFeedsMastery() throws Exception {
        long learner = user("LEARNER");
        long graphId = assignedGraph(learner);
        long nodeId = nodeIds(learner, graphId).get("移项");

        String good = json.writeValueAsString(Map.of("explanation", "移项就是把一项换个符号搬到等号另一边，例如 x + 3 = 5 变成 x = 5 − 3。"));
        JsonNode a = call(post("/api/graphs/" + graphId + "/nodes/" + nodeId + "/feynman").contentType(MediaType.APPLICATION_JSON)
                .content(good), learner, status().isOk());
        assertThat(a.get("score").asDouble()).isEqualTo(1.0);
        assertThat(a.get("followUps")).isNotEmpty();
        assertThat(a.get("mastery").asDouble()).isGreaterThan(0.5);
        assertThat(retests.findByLearnerIdAndDoneFalseOrderByDueAtAsc(learner)).hasSize(3);

        // 回答追问：连同上一次讲解一起评分
        String follow = json.writeValueAsString(Map.of("explanation", "就像把东西从桌子一边挪到另一边要翻个面。",
                "parentAttemptId", a.get("id").asLong()));
        JsonNode b = call(post("/api/graphs/" + graphId + "/nodes/" + nodeId + "/feynman").contentType(MediaType.APPLICATION_JSON)
                .content(follow), learner, status().isOk());
        assertThat(b.get("parentAttemptId").asLong()).isEqualTo(a.get("id").asLong());

        String weak = json.writeValueAsString(Map.of("explanation", "不太清楚"));
        JsonNode c = call(post("/api/graphs/" + graphId + "/nodes/" + nodeId + "/feynman").contentType(MediaType.APPLICATION_JSON)
                .content(weak), learner, status().isOk());
        assertThat(c.get("score").asDouble()).isZero();
        assertThat(c.get("needsReview").asBoolean()).isTrue();

        // 照片讲解需要 OCR，stub 不支持
        String photo = json.writeValueAsString(Map.of("imageBase64", "aGVsbG8="));
        call(post("/api/graphs/" + graphId + "/nodes/" + nodeId + "/feynman").contentType(MediaType.APPLICATION_JSON)
                .content(photo), learner, status().isNotImplemented());

        assertThat(call(get("/api/learners/" + learner + "/feynman"), learner, status().isOk())).hasSize(3);
    }

    // ---------- helpers ----------

    private long assignedGraph(long learner) throws Exception {
        long bookId = upload(learner, null).at("/book/id").asLong();
        return call(post("/api/books/" + bookId + "/assign"), learner, status().isOk()).get("id").asLong();
    }

    private Map<String, Long> nodeIds(long learner, long graphId) throws Exception {
        Map<String, Long> out = new HashMap<>();
        call(get("/api/graphs/" + graphId), learner, status().isOk()).get("nodes")
                .forEach(n -> out.put(n.get("name").asText(), n.get("id").asLong()));
        return out;
    }

    private JsonNode start(long learner, long graphId, String mode, Long target) throws Exception {
        String body = json.writeValueAsString(Map.of("mode", mode, "targetNodeId", target));
        return call(post("/api/graphs/" + graphId + "/practice").contentType(MediaType.APPLICATION_JSON).content(body),
                learner, status().isOk());
    }

    private JsonNode answer(long learner, JsonNode session, boolean correct) throws Exception {
        return answer(learner, session, correct, null);
    }

    private JsonNode answer(long learner, JsonNode session, boolean correct, String errorType) throws Exception {
        long itemId = session.at("/next/id").asLong();
        String answer = correct ? questions.findById(session.at("/next/questionId").asLong()).orElseThrow().getAnswer() : "不知道";
        Map<String, Object> body = new HashMap<>(Map.of("answer", answer, "selfConfidence", "SURE"));
        if (errorType != null) {
            body.put("errorType", errorType);
        }
        return call(post("/api/practice/" + session.get("id").asLong() + "/items/" + itemId + "/answer")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), learner, status().isOk())
                .get("session");
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
