package com.learnmap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.evidence.EvidenceRepository;
import com.learnmap.evidence.EvidenceResult;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import com.learnmap.question.QuestionSolution;
import com.learnmap.question.QuestionSolutionRepository;
import com.learnmap.question.QuestionSource;
import com.learnmap.question.QuestionType;
import com.learnmap.question.SolutionStep;
import com.learnmap.question.SolutionStepRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearningFlowIntegrationTest {

    static final String BOOK = """
            # 第一章 方程
            ## 1.1 等式的性质
            等式两边同时加上同一个数，等式仍然成立。
            ## 1.2 移项
            把方程中的某一项改变符号后，从方程的一边移到另一边，叫做移项。
            ## 1.3 解一元一次方程
            解一元一次方程的步骤是去分母、去括号、移项、合并同类项、系数化为 1。
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired KnowledgeNodeRepository nodes;
    @Autowired QuestionRepository questions;
    @Autowired QuestionSolutionRepository solutions;
    @Autowired SolutionStepRepository steps;
    @Autowired EvidenceRepository evidences;

    @Test
    void learnerUploadIsPrivateUnreviewedAndCopiedOncePerLearner() throws Exception {
        long me = user("LEARNER");
        long other = user("LEARNER");

        JsonNode imported = upload(me, null);
        long bookId = imported.at("/book/id").asLong();
        assertThat(imported.at("/book/visibility").asText()).isEqualTo("PRIVATE");
        assertThat(imported.at("/book/reviewMode").asText()).isEqualTo("NONE");
        assertThat(imported.at("/graphStatus").asText()).isEqualTo("PUBLISHED");

        call(get("/api/books/" + bookId), other, status().isForbidden());

        JsonNode copy = call(post("/api/books/" + bookId + "/assign"), me, status().isOk());
        assertThat(copy.get("kind").asText()).isEqualTo("LEARNER_COPY");
        assertThat(copy.get("reviewStatus").asText()).isEqualTo("UNREVIEWED");
        JsonNode again = call(post("/api/books/" + bookId + "/assign"), me, status().isOk());
        assertThat(again.get("id").asLong()).isEqualTo(copy.get("id").asLong());

        JsonNode graph = call(get("/api/graphs/" + copy.get("id").asLong()), me, status().isOk());
        assertThat(graph.get("nodes")).extracting(n -> n.get("name").asText())
                .containsExactly("等式的性质", "移项", "解一元一次方程");
        assertThat(graph.get("relations")).hasSize(2);
        call(get("/api/graphs/" + copy.get("id").asLong()), other, status().isForbidden());
    }

    @Test
    void tutorReviewsBeforePublishingAndAssignsOnlyToOwnLearners() throws Exception {
        long tutor = user("TUTOR");
        long learner = user("LEARNER");
        long stranger = user("LEARNER");
        call(post("/api/tutor/learners/" + learner), tutor, status().isNoContent());

        JsonNode imported = upload(tutor, "REVIEW_BEFORE_PUBLISH");
        long bookId = imported.at("/book/id").asLong();
        long masterId = imported.get("graphId").asLong();
        assertThat(imported.at("/graphStatus").asText()).isEqualTo("DRAFT");

        // 学习者看得到书，但草稿图谱看不到、也分配不了
        call(get("/api/books/" + bookId), learner, status().isOk());
        call(get("/api/graphs/" + masterId), learner, status().isForbidden());
        call(post("/api/books/" + bookId + "/assign?learnerId=" + learner), tutor, status().isBadRequest());

        JsonNode published = call(post("/api/graphs/" + masterId + "/publish?reviewed=true"), tutor, status().isOk());
        assertThat(published.get("reviewStatus").asText()).isEqualTo("REVIEWED");

        JsonNode copy = call(post("/api/books/" + bookId + "/assign?learnerId=" + learner), tutor, status().isOk());
        JsonNode graph = call(get("/api/graphs/" + copy.get("id").asLong()), tutor, status().isOk());
        assertThat(graph.get("nodes")).allMatch(n -> n.get("reviewStatus").asText().equals("CONFIRMED"));

        call(get("/api/books/" + bookId), stranger, status().isForbidden());
        call(post("/api/books/" + bookId + "/assign?learnerId=" + stranger), tutor, status().isForbidden());
    }

    @Test
    void stepAnswersCreditStepsBeforeTheMistakeAndBlameOnlyTheWrongStep() throws Exception {
        long learner = user("LEARNER");
        JsonNode imported = upload(learner, null);
        long bookId = imported.at("/book/id").asLong();
        List<KnowledgeNode> master = nodes.findByGraphIdOrderByOrdinal(imported.get("graphId").asLong());
        KnowledgeNode property = master.get(0), transpose = master.get(1), solve = master.get(2);
        long copyId = call(post("/api/books/" + bookId + "/assign"), learner, status().isOk()).get("id").asLong();

        Question q = new Question();
        q.setBookId(bookId);
        q.setNodeId(solve.getId());
        q.setQuestionType(QuestionType.STEP_FILL);
        q.setStem("解方程 2x + 3 = 7");
        q.setAnswer("2");
        q.setDifficulty(0.5);
        q.setSource(QuestionSource.MANUAL);
        q = questions.save(q);
        QuestionSolution sol = new QuestionSolution();
        sol.setQuestionId(q.getId());
        sol.setName("移项法");
        sol = solutions.save(sol);
        SolutionStep s1 = step(sol, 0, transpose, "2x=4");
        SolutionStep s2 = step(sol, 1, property, "x=2");
        SolutionStep s3 = step(sol, 2, solve, "2");

        String body = json.writeValueAsString(Map.of(
                "questionId", q.getId(), "solutionId", sol.getId(),
                "stepAnswers", Map.of(s1.getId(), "2x = 4", s2.getId(), "x=4", s3.getId(), "4")));
        JsonNode result = call(post("/api/answers").contentType(MediaType.APPLICATION_JSON).content(body),
                learner, status().isOk());

        assertThat(result.get("correct").asBoolean()).isFalse();
        assertThat(result.get("updates")).extracting(u -> u.get("nodeName").asText() + ":" + u.get("result").asText())
                .containsExactly("移项:CORRECT", "等式的性质:INCORRECT");
        // 证据写在学习者副本的节点上
        KnowledgeNode copyTranspose = nodes.findByGraphIdAndOriginNodeIdIn(copyId, List.of(transpose.getId())).get(0);
        assertThat(evidences.findByLearnerIdAndNodeIdOrderByIdAsc(learner, copyTranspose.getId()))
                .extracting(e -> e.getResult()).containsExactly(EvidenceResult.CORRECT);

        JsonNode graph = call(get("/api/graphs/" + copyId), learner, status().isOk());
        JsonNode solveView = graph.get("nodes").get(2);
        assertThat(solveView.get("mastery").isNull()).isTrue(); // 出错之后的步骤没有证据
    }

    @Test
    void learnerCorrectionsOnlyTouchTheirOwnCopy() throws Exception {
        long tutor = user("TUTOR");
        long a = user("LEARNER");
        long b = user("LEARNER");
        call(post("/api/tutor/learners/" + a), tutor, status().isNoContent());
        call(post("/api/tutor/learners/" + b), tutor, status().isNoContent());
        long bookId = upload(tutor, null).at("/book/id").asLong();
        long copyA = call(post("/api/books/" + bookId + "/assign?learnerId=" + a), tutor, status().isOk()).get("id").asLong();
        long copyB = call(post("/api/books/" + bookId + "/assign?learnerId=" + b), tutor, status().isOk()).get("id").asLong();

        JsonNode graphA = call(get("/api/graphs/" + copyA), a, status().isOk());
        long nodeId = graphA.get("nodes").get(0).get("id").asLong();
        long relId = graphA.get("relations").get(0).get("id").asLong();

        call(delete("/api/graphs/" + copyA + "/nodes/" + nodeId), b, status().isForbidden());
        call(delete("/api/graphs/" + copyA + "/nodes/" + nodeId), a, status().isNoContent());
        call(post("/api/graphs/" + copyA + "/relations/" + relId + "/dispute"), a, status().isNoContent());

        assertThat(call(get("/api/graphs/" + copyA), a, status().isOk()).get("nodes")).hasSize(2);
        assertThat(call(get("/api/graphs/" + copyB), b, status().isOk()).get("nodes")).hasSize(3);
    }

    // ---------- helpers ----------

    private SolutionStep step(QuestionSolution sol, int ordinal, KnowledgeNode node, String answer) {
        SolutionStep s = new SolutionStep();
        s.setSolutionId(sol.getId());
        s.setOrdinal(ordinal);
        s.setDescription("第 " + (ordinal + 1) + " 步");
        s.setNodeId(node.getId());
        s.setAnswer(answer);
        return steps.save(s);
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
