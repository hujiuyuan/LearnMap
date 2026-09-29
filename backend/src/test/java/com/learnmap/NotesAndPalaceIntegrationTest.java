package com.learnmap;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 1b：笔记（康奈尔 / 卡片盒 / MOC）和记忆宫殿的接口 */
class NotesAndPalaceIntegrationTest extends ApiTestSupport {

    private JsonNode saveNote(long user, Long id, Map<String, Object> body, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        var req = id == null ? post("/api/notes") : put("/api/notes/" + id);
        return call(req.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), user, expected);
    }

    private List<String> dueFronts(long learner) throws Exception {
        List<String> out = new ArrayList<>();
        call(get("/api/cards/due?limit=100"), learner, status().isOk()).get("cards").forEach(c -> out.add(c.get("front").asText()));
        return out;
    }

    @Test
    void cornellCuesBecomeRecallCardsAndSummaryGetsFeedback() throws Exception {
        long learner = user("LEARNER");
        long graph = assignedGraph(learner);
        long node = nodeIds(learner, graph).get("移项");

        saveNote(learner, null, Map.of("kind", "CORNELL", "title", "移项", "cues", "x", "nodeIds", List.of()), status().isBadRequest());

        JsonNode note = saveNote(learner, null, Map.of("kind", "CORNELL", "title", "移项笔记",
                "cues", "- 移项要注意什么？\n1. 移项的依据是什么？\n\n", "body", "移项要变号，依据是等式的性质。",
                "summary", "移项就是把一项变号后移到另一边", "nodeIds", List.of(node)), status().isCreated());
        long id = note.get("id").asLong();
        assertThat(note.get("cueCards").asInt()).isEqualTo(2);
        assertThat(dueFronts(learner)).contains("移项要注意什么？", "移项的依据是什么？");

        saveNote(learner, id, Map.of("kind", "CORNELL", "title", "移项笔记", "cues", "移项要注意什么？",
                "body", "移项要变号", "nodeIds", List.of(node)), status().isOk());
        assertThat(dueFronts(learner)).contains("移项要注意什么？").doesNotContain("移项的依据是什么？");

        saveNote(learner, id, Map.of("kind", "CORNELL", "title", "移项笔记", "cues", "移项要注意什么？",
                "body", "移项要变号", "summary", "移项就是把一项变号后移到另一边，比如 x+1=3 变成 x=3-1",
                "nodeIds", List.of(node)), status().isOk());
        JsonNode fb = call(post("/api/notes/" + id + "/feedback"), learner, status().isOk());
        assertThat(fb.has("covered")).isTrue();
        assertThat(fb.has("score")).isFalse();
        assertThat(call(get("/api/notes/" + id), learner, status().isOk()).at("/feedback/covered").isArray()).isTrue();

        call(delete("/api/notes/" + id), learner, status().isNoContent());
        assertThat(dueFronts(learner)).doesNotContain("移项要注意什么？");
    }

    @Test
    void zettelWikiLinksBacklinksSuggestionsMocAndProfile() throws Exception {
        long learner = user("LEARNER");
        long graph = assignedGraph(learner);
        Map<String, Long> ids = nodeIds(learner, graph);

        long a = saveNote(learner, null, Map.of("kind", "ZETTEL", "title", "移项要变号",
                "body", "把一项从等号一边移到另一边时要改变符号", "nodeIds", List.of(ids.get("移项"))), status().isCreated()).get("id").asLong();
        JsonNode b = saveNote(learner, null, Map.of("kind", "ZETTEL", "title", "等式两边同加",
                "body", "等式两边同时加同一个数仍然相等，所以 [[移项要变号]] 是它的推论。另见 [[还没写的卡]]",
                "nodeIds", List.of(ids.get("等式的性质"))), status().isCreated());
        long bId = b.get("id").asLong();
        assertThat(b.get("links").get(0).get("noteId").asLong()).isEqualTo(a);
        assertThat(b.get("links").get(0).get("outgoing").asBoolean()).isTrue();
        assertThat(b.get("unresolved").get(0).asText()).isEqualTo("还没写的卡");

        JsonNode aView = call(get("/api/notes/" + a), learner, status().isOk());
        assertThat(aView.get("links").get(0).get("noteId").asLong()).isEqualTo(bId);
        assertThat(aView.get("links").get(0).get("outgoing").asBoolean()).isFalse();

        long c = saveNote(learner, null, Map.of("kind", "ZETTEL", "title", "变号",
                "body", "移到另一边时要改变符号，这一步最容易忘", "nodeIds", List.of(ids.get("移项"))), status().isCreated()).get("id").asLong();
        JsonNode sugg = call(get("/api/notes/" + a + "/link-suggestions"), learner, status().isOk());
        assertThat(sugg.findValuesAsText("noteId")).contains(String.valueOf(c));
        assertThat(call(post("/api/notes/" + a + "/links/" + c + "?suggested=true"), learner, status().isOk()).get("links")).hasSize(2);
        assertThat(call(delete("/api/notes/" + a + "/links/" + c), learner, status().isOk()).get("links")).hasSize(1);

        // 去掉正文里的 [[ ]]，自动链接跟着删
        saveNote(learner, bId, Map.of("kind", "ZETTEL", "title", "等式两边同加", "body", "等式两边同时加同一个数仍然相等",
                "nodeIds", List.of(ids.get("等式的性质"))), status().isOk());
        assertThat(call(get("/api/notes/" + a), learner, status().isOk()).get("links")).isEmpty();

        JsonNode list = call(get("/api/learners/" + learner + "/notes?nodeId=" + ids.get("移项")), learner, status().isOk());
        assertThat(list).hasSize(2);

        JsonNode moc = call(get("/api/graphs/" + graph + "/moc-draft"), learner, status().isOk());
        assertThat(moc.get("body").asText()).contains("移项", "[[移项要变号]]", "还没有笔记的知识点", "解一元一次方程");
        JsonNode mocNote = saveNote(learner, null, Map.of("kind", "MOC", "title", moc.get("title").asText(),
                "body", moc.get("body").asText(), "nodeIds", json.convertValue(moc.get("nodeIds"), List.class)), status().isCreated());
        assertThat(mocNote.get("links").size()).isGreaterThanOrEqualTo(2);

        JsonNode notes = call(get("/api/learners/" + learner + "/profile"), learner, status().isOk()).get("notes");
        assertThat(notes.get("zettel").asInt()).isEqualTo(3);
        assertThat(notes.get("moc").asInt()).isEqualTo(1);
        assertThat(notes.get("atomicRate").asDouble()).isEqualTo(1.0);
    }

    @Test
    void notesStayWithTheirOwner() throws Exception {
        long learner = user("LEARNER");
        long other = user("LEARNER");
        long graph = assignedGraph(learner);
        long node = nodeIds(learner, graph).get("移项");
        long id = saveNote(learner, null, Map.of("kind", "ZETTEL", "title", "我的卡", "body", "内容", "nodeIds", List.of(node)),
                status().isCreated()).get("id").asLong();

        saveNote(other, null, Map.of("kind", "ZETTEL", "title", "偷挂", "nodeIds", List.of(node)), status().isForbidden());
        call(get("/api/notes/" + id), other, status().isForbidden());
        call(get("/api/learners/" + learner + "/notes"), other, status().isForbidden());
        saveNote(other, id, Map.of("kind", "ZETTEL", "title", "改", "nodeIds", List.of()), status().isForbidden());

        long tutor = user("TUTOR");
        call(post("/api/tutor/learners/" + learner), tutor, status().isNoContent());
        assertThat(call(get("/api/learners/" + learner + "/notes"), tutor, status().isOk())).hasSize(1);
    }

    @Test
    void buildPalaceWalkItPlaceContentAndRecall() throws Exception {
        long learner = user("LEARNER");
        long graph = assignedGraph(learner);
        long palace = postJson("/api/palaces", Map.of("name", "我家", "place", "家里", "graphId", graph), learner, status().isCreated())
                .get("id").asLong();

        // 地点太少、重复都不行
        call(put("/api/palaces/" + palace + "/loci").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"鞋柜\",\"沙发\",\"鞋柜\",\"电视\",\"餐桌\"]}"), learner, status().isBadRequest());
        List<String> loci = List.of("门口鞋柜", "沙发", "电视", "餐桌", "冰箱");
        JsonNode p = call(put("/api/palaces/" + palace + "/loci").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("names", loci))), learner, status().isOk());
        assertThat(p.get("status").asText()).isEqualTo("DRILLING");

        // 没建成不能挂内容
        long firstLocus = p.at("/loci/0/id").asLong();
        call(put("/api/palaces/" + palace + "/placements").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("[{\"locusId\":" + firstLocus + ",\"content\":\"x\"}]"), learner, status().isBadRequest());

        assertThat(postJson("/api/palaces/" + palace + "/drill", Map.of("mode", "FORWARD",
                "answers", List.of("鞋柜", "沙发", "餐桌", "电视", "冰箱")), learner, status().isOk()).get("correct").asBoolean()).isFalse();
        assertThat(postJson("/api/palaces/" + palace + "/drill", Map.of("mode", "FORWARD",
                "answers", List.of("鞋柜", "沙发", "电视", "餐桌", "冰箱")), learner, status().isOk()).get("correct").asBoolean()).isTrue();
        postJson("/api/palaces/" + palace + "/drill", Map.of("mode", "BACKWARD",
                "answers", List.of("冰箱", "餐桌", "电视", "沙发", "门口鞋柜")), learner, status().isOk());
        JsonNode last = null;
        for (int i = 0; i < 3; i++) {
            JsonNode q = call(get("/api/palaces/" + palace + "/drill/next"), learner, status().isOk());
            int from = q.get("ordinal").asInt();
            last = postJson("/api/palaces/" + palace + "/drill", Map.of("mode", "NEXT", "fromOrdinal", from,
                    "answer", loci.get(from)), learner, status().isOk());
            assertThat(last.get("correct").asBoolean()).isTrue();
        }
        assertThat(last.at("/palace/status").asText()).isEqualTo("READY");

        JsonNode candidates = call(get("/api/palaces/" + palace + "/candidates"), learner, status().isOk());
        assertThat(candidates).hasSize(3);
        List<Map<String, Object>> items = new ArrayList<>();
        JsonNode lociView = last.at("/palace/loci");
        for (int i = 0; i < 3; i++) {
            items.add(Map.of("locusId", lociView.get(i).get("id").asLong(), "nodeId", candidates.get(i).get("id").asLong()));
        }
        Map<String, Object> custom = new HashMap<>();
        custom.put("locusId", lociView.get(3).get("id").asLong());
        custom.put("content", "合并同类项");
        items.add(custom);
        JsonNode placed = call(put("/api/palaces/" + palace + "/placements").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(items)), learner, status().isOk());
        assertThat(placed.at("/loci/0/placement/content").asText()).isEqualTo(candidates.get(0).get("name").asText());

        JsonNode withImages = call(post("/api/palaces/" + palace + "/images"), learner, status().isOk());
        String img = withImages.at("/loci/0/placement/image").asText();
        assertThat(img).contains("门口鞋柜");
        long placementId = withImages.at("/loci/0/placement/id").asLong();
        JsonNode edited = call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/palaces/placements/" + placementId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"image\":\"我自己编的画面\"}"), learner, status().isOk());
        assertThat(edited.at("/loci/0/placement/ownImage").asBoolean()).isTrue();

        assertThat(call(get("/api/palaces/due"), learner, status().isOk()).get("dueCount").asLong()).isEqualTo(1);

        List<String> answers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            answers.add(candidates.get(i).get("name").asText());
        }
        answers.add("不记得了");
        JsonNode recall = postJson("/api/palaces/" + palace + "/recall", Map.of("answers", answers), learner, status().isOk());
        assertThat(recall.get("correct").asInt()).isEqualTo(3);
        assertThat(recall.get("total").asInt()).isEqualTo(4);
        assertThat(recall.get("nextWalkAt").isNull()).isFalse();
        assertThat(call(get("/api/palaces/due"), learner, status().isOk()).get("dueCount").asLong()).isZero();

        JsonNode modes = call(get("/api/learners/" + learner + "/profile"), learner, status().isOk()).at("/modes");
        assertThat(modes.toString()).contains("记忆宫殿");

        JsonNode cleared = call(post("/api/palaces/" + palace + "/clear"), learner, status().isOk());
        assertThat(cleared.at("/loci/0/placement").isNull()).isTrue();
        assertThat(cleared.get("status").asText()).isEqualTo("READY");

        long other = user("LEARNER");
        call(get("/api/palaces/" + palace), other, status().isForbidden());
    }
}
