package com.learnmap.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnmap.ApiTestSupport;
import com.learnmap.retest.RetestReason;
import com.learnmap.retest.ScheduledRetest;
import com.learnmap.retest.ScheduledRetestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TutorIntegrationTest extends ApiTestSupport {

    @Autowired ScheduledRetestRepository retests;

    @Test
    void tutorSeesDashboardTracesRootCauseFlagsAndReviewsFeynman() throws Exception {
        long tutor = user("TUTOR");
        long learner = user("LEARNER");
        call(post("/api/tutor/learners/" + learner), tutor, status().isNoContent());
        long graphId = assignedGraph(learner);
        Map<String, Long> nodes = nodeIds(learner, graphId);

        // 学习者：“等式的性质”连错两次，“解一元一次方程”错一次
        wrong(learner, graphId, nodes.get("等式的性质"), "CONCEPT_CONFUSION");
        wrong(learner, graphId, nodes.get("等式的性质"), null);
        wrong(learner, graphId, nodes.get("解一元一次方程"), "CALCULATION");
        // 一次讲得很差的费曼讲解 → 待复核
        JsonNode attempt = postJson("/api/graphs/" + graphId + "/nodes/" + nodes.get("移项") + "/feynman",
                Map.of("explanation", "不知道"), learner, status().isOk());
        assertThat(attempt.get("needsReview").asBoolean()).isTrue();

        JsonNode dashboard = call(get("/api/tutor/dashboard"), tutor, status().isOk());
        assertThat(dashboard).hasSize(1);
        JsonNode card = dashboard.get(0);
        assertThat(card.get("learnerId").asLong()).isEqualTo(learner);
        assertThat(card.get("total").asInt()).isEqualTo(3);
        assertThat(card.get("feynmanToReview").asInt()).isEqualTo(1);
        assertThat(card.get("streak").asInt()).isEqualTo(1);
        assertThat(card.get("alerts").toString()).contains("费曼讲解等你复核");
        call(get("/api/tutor/dashboard"), learner, status().isForbidden());
        JsonNode heat = call(get("/api/tutor/heatmap"), tutor, status().isOk());
        assertThat(heat.get("columns").get(0).asText()).isEqualTo("一元一次方程 · 第一章 方程");
        assertThat(heat.at("/rows/0/values/0").asDouble()).isLessThan(0.5);

        // 追溯：解一元一次方程 ← 移项 ← 等式的性质（薄弱），根因是等式的性质
        JsonNode trace = call(get("/api/learners/" + learner + "/nodes/" + nodes.get("解一元一次方程") + "/trace"), tutor, status().isOk());
        assertThat(trace.get("prerequisites")).hasSize(2);
        assertThat(trace.get("rootCauses").toString()).contains("等式的性质");
        assertThat(trace.get("explanation").asText()).contains("先补");
        assertThat(trace.get("recent").get(0).get("errorType").asText()).isEqualTo("CALCULATION");

        // 画像
        JsonNode profile = call(get("/api/learners/" + learner + "/profile"), tutor, status().isOk());
        assertThat(profile.at("/knowledge/total").asInt()).isEqualTo(3);
        assertThat(profile.at("/errors/wrongTotal").asInt()).isEqualTo(3);
        assertThat(profile.at("/abilities/expression").asDouble()).isLessThan(0.4);
        call(get("/api/learners/" + learner + "/profile"), user("LEARNER"), status().isForbidden());
        JsonNode highlights = call(get("/api/learners/" + learner + "/highlights"), learner, status().isOk());
        assertThat(highlights.get("total").asInt()).isEqualTo(3);

        // 标记“诊断不对” → 马上安排一次复测验证；运维能看到汇总
        long weak = nodes.get("等式的性质");
        postJson("/api/learners/" + learner + "/nodes/" + weak + "/flags", Map.of("kind", "DIAGNOSIS_WRONG", "note", "课上答得很好"),
                learner, status().isForbidden());
        JsonNode flag = postJson("/api/learners/" + learner + "/nodes/" + weak + "/flags",
                Map.of("kind", "DIAGNOSIS_WRONG", "note", "课上答得很好"), tutor, status().isOk());
        assertThat(flag.get("nodeName").asText()).isEqualTo("等式的性质");
        assertThat(retests.findByLearnerIdAndDoneFalseOrderByDueAtAsc(learner))
                .extracting(ScheduledRetest::getReason).contains(RetestReason.TUTOR_CHECK);
        assertThat(call(get("/api/retests/due"), learner, status().isOk()).toString()).contains("TUTOR_CHECK");
        JsonNode ops = call(get("/api/ops/diagnosis-flags"), user("OPS"), status().isOk());
        assertThat(ops.toString()).contains("等式的性质");
        call(get("/api/ops/diagnosis-flags"), tutor, status().isForbidden());

        // 复核费曼：给高分 → 写一条证据、清掉待复核
        JsonNode pending = call(get("/api/tutor/feynman-reviews"), tutor, status().isOk());
        assertThat(pending).hasSize(1);
        JsonNode reviewed = postJson("/api/feynman/" + attempt.get("id").asLong() + "/review",
                Map.of("score", 0.9, "note", "口头讲得挺清楚"), tutor, status().isOk());
        assertThat(reviewed.get("reviewedScore").asDouble()).isEqualTo(0.9);
        assertThat(reviewed.get("needsReview").asBoolean()).isFalse();
        assertThat(reviewed.get("mastery").asDouble()).isGreaterThan(0);
        assertThat(call(get("/api/tutor/feynman-reviews"), tutor, status().isOk())).isEmpty();
        postJson("/api/feynman/" + attempt.get("id").asLong() + "/review", Map.of("score", 0.9), learner, status().isForbidden());
    }

    @Test
    void firstPrinciplesThatStaysStuckIsFlaggedForTheTutor() throws Exception {
        long tutor = user("TUTOR");
        long learner = user("LEARNER");
        call(post("/api/tutor/learners/" + learner), tutor, status().isNoContent());
        long graphId = assignedGraph(learner);
        Map<String, Long> nodes = nodeIds(learner, graphId);

        JsonNode s = postJson("/api/graphs/" + graphId + "/practice",
                Map.of("mode", "FIRST_PRINCIPLES", "targetNodeId", nodes.get("解一元一次方程")), learner, status().isOk());
        for (int i = 0; i < 60 && !s.get("next").isNull(); i++) {
            s = postJson("/api/practice/" + s.get("id").asLong() + "/items/" + s.at("/next/id").asLong() + "/answer",
                    Map.of("answer", "不知道"), learner, status().isOk()).get("session");
        }
        assertThat(s.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(s.get("summary").asText()).contains("标记给辅导者");

        JsonNode flags = call(get("/api/learners/" + learner + "/flags"), tutor, status().isOk());
        assertThat(flags).anySatisfy(f -> {
            assertThat(f.get("kind").asText()).isEqualTo("STUCK");
            assertThat(f.get("nodeName").asText()).isEqualTo("等式的性质");
        });
        assertThat(call(get("/api/tutor/dashboard"), tutor, status().isOk()).get(0).get("alerts").toString()).contains("卡住");
        long flagId = flags.get(0).get("id").asLong();
        assertThat(call(post("/api/flags/" + flagId + "/resolve"), tutor, status().isOk()).get("resolved").asBoolean()).isTrue();
    }

    private void wrong(long learner, long graphId, long nodeId, String errorType) throws Exception {
        JsonNode qs = call(get("/api/graphs/" + graphId + "/nodes/" + nodeId + "/questions"), learner, status().isOk());
        Map<String, Object> body = new HashMap<>(Map.of("questionId", qs.get(0).get("id").asLong(),
                "activityType", "QUIZ", "answer", "不知道", "selfConfidence", "SURE"));
        if (errorType != null) {
            body.put("errorType", errorType);
        }
        postJson("/api/answers", body, learner, status().isOk());
    }
}
