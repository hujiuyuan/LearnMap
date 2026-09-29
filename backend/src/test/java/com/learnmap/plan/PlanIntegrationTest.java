package com.learnmap.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnmap.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanIntegrationTest extends ApiTestSupport {

    @Test
    void goalProducesTodaysPlanInBlocksAndReplanningKeepsDoneTasks() throws Exception {
        long learner = user("LEARNER");
        long graphId = assignedGraph(learner);

        JsonNode plan = postJson("/api/goals", Map.of("graphId", graphId, "scope", "BOOK",
                "deadline", LocalDate.now().plusDays(30).toString(), "weekdayMinutes", 60, "weekendMinutes", 60,
                "mainStyle", "UNDERSTAND", "rhythm", "POMODORO"), learner, status().isOk());
        assertThat(plan.at("/goal/warning").isNull()).isTrue();
        List<JsonNode> tasks = tasks(plan);
        // 新分配的图谱：3 个节点的卡片今天就到期，所以第一项是复习卡片
        assertThat(tasks.get(0).get("type").asText()).isEqualTo("REVIEW_CARDS");
        assertThat(tasks).anySatisfy(t -> assertThat(t.get("nodeName").asText()).isEqualTo("等式的性质"));
        assertThat(tasks).allSatisfy(t -> assertThat(t.get("reason").asText()).isNotBlank());
        assertThat(plan.get("blocks").size()).isGreaterThanOrEqualTo(2);
        assertThat(plan.get("blocks")).allSatisfy(b -> assertThat(b.get("minutes").asInt()).isLessThanOrEqualTo(25));

        // 完成第一项，重排后它还在、仍是完成状态，今天不再重复安排复习卡片
        long first = tasks.get(0).get("id").asLong();
        postJson("/api/plan/tasks/" + first, Map.of("status", "DONE"), learner, status().isOk());
        JsonNode replanned = call(post("/api/learners/" + learner + "/plan/replan"), learner, status().isOk());
        List<JsonNode> after = tasks(replanned);
        assertThat(after.get(0).get("id").asLong()).isEqualTo(first);
        assertThat(after.get(0).get("status").asText()).isEqualTo("DONE");
        assertThat(after.stream().filter(t -> t.get("type").asText().equals("REVIEW_CARDS"))).hasSize(1);
        assertThat(replanned.get("doneMinutes").asInt()).isEqualTo(tasks.get(0).get("minutes").asInt());

        // 番茄钟
        JsonNode pomo = postJson("/api/pomodoros", Map.of("taskId", after.get(1).get("id").asLong()), learner, status().isOk());
        assertThat(pomo.get("plannedMinutes").asInt()).isEqualTo(25);
        JsonNode finished = postJson("/api/pomodoros/" + pomo.get("id").asLong() + "/finish",
                Map.of("completed", true, "interruptions", 1), learner, status().isOk());
        assertThat(finished.get("completed").asBoolean()).isTrue();
        assertThat(call(get("/api/pomodoros"), learner, status().isOk())).hasSize(1);
    }

    @Test
    void tightDeadlineWarnsAndTutorCanSetTheGoal() throws Exception {
        long tutor = user("TUTOR");
        long learner = user("LEARNER");
        call(post("/api/tutor/learners/" + learner), tutor, status().isNoContent());
        long graphId = assignedGraph(learner);
        long stranger = user("LEARNER");

        Map<String, Object> goal = Map.of("graphId", graphId, "scope", "BOOK", "deadline", LocalDate.now().toString(),
                "weekdayMinutes", 5, "weekendMinutes", 5, "mainStyle", "PRACTICE", "rhythm", "MICRO");
        postJson("/api/goals", goal, stranger, status().isForbidden());
        JsonNode plan = postJson("/api/goals", goal, tutor, status().isOk());
        assertThat(plan.at("/goal/warning").asText()).isEqualTo(PlanGenerator.BEHIND_WARNING);
        // 辅导者能看，学习者本人看到的是同一份计划
        JsonNode mine = call(get("/api/learners/" + learner + "/plan"), learner, status().isOk());
        assertThat(mine.at("/goal/id").asLong()).isEqualTo(plan.at("/goal/id").asLong());
        call(get("/api/learners/" + learner + "/plan"), stranger, status().isForbidden());

        postJson("/api/goals", Map.of("graphId", graphId, "scope", "BOOK", "deadline", LocalDate.now().minusDays(1).toString(),
                "weekdayMinutes", 30, "weekendMinutes", 30), learner, status().isBadRequest());
    }

    static List<JsonNode> tasks(JsonNode plan) {
        List<JsonNode> out = new ArrayList<>();
        plan.get("blocks").forEach(b -> b.get("tasks").forEach(out::add));
        return out;
    }
}
