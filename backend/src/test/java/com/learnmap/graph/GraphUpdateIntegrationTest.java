package com.learnmap.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnmap.ApiTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GraphUpdateIntegrationTest extends ApiTestSupport {

    @Test
    void tutorEditsNewVersionAndChoosesMergeOrReplaceForEachLearner() throws Exception {
        long tutor = user("TUTOR");
        long a = user("LEARNER");
        long b = user("LEARNER");
        call(post("/api/tutor/learners/" + a), tutor, status().isNoContent());
        call(post("/api/tutor/learners/" + b), tutor, status().isNoContent());

        // 辅导者上传、先校对再发布 v1，分给两个学习者
        JsonNode imported = upload(tutor, BOOK, "REVIEW_BEFORE_PUBLISH");
        long bookId = imported.at("/book/id").asLong();
        long v1 = imported.get("graphId").asLong();
        call(post("/api/graphs/" + v1 + "/publish?reviewed=true"), tutor, status().isOk());
        long copyA = call(post("/api/books/" + bookId + "/assign?learnerId=" + a), tutor, status().isOk()).get("id").asLong();
        long copyB = call(post("/api/books/" + bookId + "/assign?learnerId=" + b), tutor, status().isOk()).get("id").asLong();
        Map<String, Long> nodesA = nodeIds(a, copyA);
        Map<String, Long> nodesB = nodeIds(b, copyB);
        long oldQuestionA = answer(a, copyA, nodesA.get("等式的性质"));
        answer(a, copyA, nodesA.get("解一元一次方程"));
        answer(b, copyB, nodesB.get("等式的性质"));

        // 重新生成：假 AI 每次结果一样，差异为空
        JsonNode regen = call(post("/api/books/" + bookId + "/regenerate"), tutor, status().isOk());
        long v2 = regen.at("/graph/id").asLong();
        assertThat(regen.at("/graph/status").asText()).isEqualTo("DRAFT");
        assertThat(regen.at("/diffFromPublished/empty").asBoolean()).isTrue();

        // 辅导者在编辑器里改 v2：改两个定义、驳回一个节点、加一个节点和关系
        Map<String, Long> v2Nodes = nodeIds(tutor, v2);
        call(patch("/api/editor/graphs/" + v2 + "/nodes/" + v2Nodes.get("等式的性质"))
                .contentType("application/json").content("{\"definition\":\"等式两边同时加减同一个数，等式仍成立（新版）\"}"), tutor, status().isOk());
        call(patch("/api/editor/graphs/" + v2 + "/nodes/" + v2Nodes.get("移项"))
                .contentType("application/json").content("{\"definition\":\"把某项从等号一边移到另一边要变号（新版）\"}"), tutor, status().isOk());
        postJson("/api/editor/graphs/" + v2 + "/nodes/" + v2Nodes.get("解一元一次方程") + "/review", Map.of("status", "REJECTED"),
                tutor, status().isOk());
        JsonNode added = postJson("/api/editor/graphs/" + v2 + "/nodes", Map.of("name", "检验方程的解",
                "definition", "把解代回原方程，看左右两边是否相等", "type", "METHOD"), tutor, status().isCreated());
        JsonNode withRel = postJson("/api/editor/graphs/" + v2 + "/relations", Map.of("fromNodeId", v2Nodes.get("移项"),
                "toNodeId", added.get("id").asLong(), "type", "PREREQUISITE"), tutor, status().isOk());
        assertThat(withRel.get("relations").toString()).contains("移项", "人工添加");
        postJson("/api/editor/graphs/" + v2 + "/nodes", Map.of("name", "x", "type", "FACT"), a, status().isForbidden());
        assertThat(call(get("/api/graphs/" + v2 + "/diff?against=" + v1), tutor, status().isOk()).get("added").toString())
                .contains("检验方程的解");
        call(post("/api/graphs/" + v2 + "/publish?reviewed=true"), tutor, status().isOk());

        // 学习者看得到有新版本，但分配下来的书由辅导者选
        JsonNode updA = call(get("/api/graphs/" + copyA + "/update"), a, status().isOk());
        assertThat(updA.get("available").asBoolean()).isTrue();
        assertThat(updA.get("canDecide").asBoolean()).isFalse();
        assertThat(updA.get("decider").asText()).isEqualTo("辅导者");
        postJson("/api/graphs/" + copyA + "/update", Map.of("decision", "MERGE"), a, status().isForbidden());

        JsonNode diffA = call(get("/api/graphs/" + copyA + "/update"), tutor, status().isOk()).get("diff");
        assertThat(names(diffA.get("added"))).containsExactly("检验方程的解");
        assertThat(names(diffA.get("removed"))).containsExactly("解一元一次方程");
        assertThat(diffA.at("/removed/0/hasRecords").asBoolean()).isTrue();
        assertThat(names(diffA.get("changed"))).containsExactlyInAnyOrder("等式的性质", "移项");

        // A 合并：新增节点加进来，有学习记录的节点不变
        JsonNode merged = postJson("/api/graphs/" + copyA + "/update", Map.of("decision", "MERGE"), tutor, status().isOk());
        assertThat(merged.get("summary").asText()).contains("新增 1 个", "更新 1 个", "保持不变");
        JsonNode graphA = call(get("/api/graphs/" + copyA), a, status().isOk());
        assertThat(names(graphA.get("nodes"))).containsExactlyInAnyOrder("等式的性质", "移项", "解一元一次方程", "检验方程的解");
        assertThat(definition(graphA, "等式的性质")).doesNotContain("新版");
        assertThat(definition(graphA, "移项")).contains("新版");
        assertThat(call(get("/api/graphs/" + copyA + "/update"), a, status().isOk()).get("available").asBoolean()).isFalse();
        // 旧版题目照样能答，记到副本的同名节点上
        JsonNode r = postJson("/api/answers", Map.of("questionId", oldQuestionA, "activityType", "QUIZ",
                "answer", rightAnswer(oldQuestionA)), a, status().isOk());
        assertThat(r.at("/updates/0/nodeName").asText()).isEqualTo("等式的性质");

        // B 换成新版：没有学习记录的“解一元一次方程”删掉，新节点有了卡片和新版题目
        JsonNode replaced = postJson("/api/graphs/" + copyB + "/update", Map.of("decision", "REPLACE"), tutor, status().isOk());
        assertThat(replaced.get("summary").asText()).contains("换成新版", "删除 1 个");
        JsonNode graphB = call(get("/api/graphs/" + copyB), b, status().isOk());
        assertThat(names(graphB.get("nodes"))).containsExactlyInAnyOrder("等式的性质", "移项", "检验方程的解");
        assertThat(graphB.get("relations").toString()).contains(String.valueOf(nodeIds(b, copyB).get("检验方程的解")));
        assertThat(call(get("/api/cards/due?limit=50"), b, status().isOk()).toString()).contains("检验方程的解");
        long newNode = nodeIds(b, copyB).get("检验方程的解");
        answer(b, copyB, newNode);
    }

    @Test
    void learnerDecidesForOwnBookAndKeepLeavesTheCopyAlone() throws Exception {
        long learner = user("LEARNER");
        JsonNode imported = upload(learner, BOOK);
        long bookId = imported.at("/book/id").asLong();
        long copy = call(post("/api/books/" + bookId + "/assign"), learner, status().isOk()).get("id").asLong();
        assertThat(call(get("/api/graphs/" + copy + "/update"), learner, status().isOk()).get("available").asBoolean()).isFalse();

        // 学习者上传的书不校对，重新生成后直接发布
        JsonNode regen = call(post("/api/books/" + bookId + "/regenerate"), learner, status().isOk());
        assertThat(regen.at("/graph/status").asText()).isEqualTo("PUBLISHED");
        JsonNode upd = call(get("/api/graphs/" + copy + "/update"), learner, status().isOk());
        assertThat(upd.get("available").asBoolean()).isTrue();
        assertThat(upd.get("canDecide").asBoolean()).isTrue();
        assertThat(upd.at("/diff/empty").asBoolean()).isTrue();
        JsonNode kept = postJson("/api/graphs/" + copy + "/update", Map.of("decision", "KEEP"), learner, status().isOk());
        assertThat(kept.get("summary").asText()).contains("保留旧版");
        assertThat(call(get("/api/graphs/" + copy + "/update"), learner, status().isOk()).get("available").asBoolean()).isFalse();
        // 学习者不能用编辑器改自己的副本
        call(patch("/api/editor/graphs/" + copy + "/nodes/" + nodeIds(learner, copy).get("移项"))
                .contentType("application/json").content("{\"definition\":\"x\"}"), learner, status().isBadRequest());
        // 节点详情带原文出处
        JsonNode detail = call(get("/api/graphs/" + copy + "/nodes/" + nodeIds(learner, copy).get("移项")), learner, status().isOk());
        assertThat(detail.get("sources")).isNotEmpty();
    }

    private long answer(long learner, long graphId, long nodeId) throws Exception {
        JsonNode qs = call(get("/api/graphs/" + graphId + "/nodes/" + nodeId + "/questions"), learner, status().isOk());
        long q = qs.get(0).get("id").asLong();
        Map<String, Object> body = new HashMap<>(Map.of("questionId", q, "activityType", "QUIZ", "answer", "不知道"));
        postJson("/api/answers", body, learner, status().isOk());
        return q;
    }

    private static List<String> names(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.get("name").asText()));
        return out;
    }

    private static String definition(JsonNode graph, String name) {
        for (JsonNode n : graph.get("nodes")) {
            if (n.get("name").asText().equals(name)) {
                return n.get("definition").asText();
            }
        }
        throw new AssertionError(name);
    }
}
