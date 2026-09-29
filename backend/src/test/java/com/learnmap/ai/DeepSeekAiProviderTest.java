package com.learnmap.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.ai.AiProvider.ExtractionRequest;
import com.learnmap.ai.AiProvider.FeynmanGrade;
import com.learnmap.ai.AiProvider.GeneratedQuestion;
import com.learnmap.ai.AiProvider.NodeBrief;
import com.learnmap.graph.NodeType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用本地假服务器验证发给 DeepSeek 的请求格式、结果解析和错误处理 */
class DeepSeekAiProviderTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final Deque<int[]> statuses = new ArrayDeque<>();
    private final Deque<String> contents = new ArrayDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", ex -> {
            requests.add(json.readTree(ex.getRequestBody()));
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            int status = statuses.isEmpty() ? 200 : statuses.poll()[0];
            String body = status != 200 ? "{\"error\":{\"message\":\"x\"}}"
                    : json.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop",
                    "message", Map.of("role", "assistant", "content", contents.isEmpty() ? "" : contents.poll())))));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private DeepSeekAiProvider provider() {
        return new DeepSeekAiProvider(new AiProperties("deepseek", "test-key", null, null,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    private static final NodeBrief BRIEF = new NodeBrief("移项", "把一项变号后移到等号另一边", NodeType.METHOD, List.of());

    @Test
    void sendsJsonModeRequestAndCleansQuestions() {
        contents.add("""
                {"questions":[{"type":"SINGLE_CHOICE","stem":"x+2=5 移项得","options":["x=5-2","x=5+2"],"answer":"x=5-2","difficulty":0.3,"abilities":["CALCULATION"]},
                              {"type":"SINGLE_CHOICE","stem":"答案不在选项里","options":["a","b"],"answer":"c","difficulty":0.3,"abilities":[]}]}""");
        List<GeneratedQuestion> qs = provider().generateQuestions(BRIEF, 2);

        assertThat(qs).hasSize(1);
        assertThat(qs.get(0).answer()).isEqualTo("x=5-2");
        JsonNode req = requests.get(0);
        assertThat(req.get("model").asText()).isEqualTo("deepseek-chat");
        assertThat(req.at("/response_format/type").asText()).isEqualTo("json_object");
        assertThat(req.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(req.at("/messages/0/content").asText()).contains("json");
        assertThat(req.at("/messages/1/content").asText()).contains("移项");
        assertThat(authHeaders.get(0)).isEqualTo("Bearer test-key");
    }

    @Test
    void retriesEmptyContentAndStripsCodeFence() {
        contents.add("");
        contents.add("```json\n{\"score\":0.9,\"coveredPoints\":[\"变号\"],\"missingPoints\":[],\"incorrectStatements\":[],\"followUpQuestions\":[\"a\",\"b\",\"c\"]}\n```");
        FeynmanGrade g = provider().gradeFeynman(BRIEF, "移过去要变号");
        assertThat(g.score()).isEqualTo(0.9);
        assertThat(g.followUpQuestions()).hasSize(2);
        assertThat(requests).hasSize(2);
    }

    @Test
    void retriesServerErrorsThenSucceeds() {
        statuses.add(new int[]{503});
        contents.add("{\"images\":[\"鞋柜里跳出一个负号\"]}");
        assertThat(provider().suggestPalaceImages(List.of(new AiProvider.PalaceItem("鞋柜", "移项变号"), new AiProvider.PalaceItem("沙发", "合并同类项"))))
                .containsExactly("鞋柜里跳出一个负号", "");
    }

    @Test
    void mapsAuthErrorAndRejectsOcr() {
        statuses.add(new int[]{401});
        assertThatThrownBy(() -> provider().suggestLinks("卡片", List.of("另一张")))
                .isInstanceOf(AiException.class).hasMessageContaining("LEARNMAP_AI_API_KEY");
        assertThatThrownBy(() -> provider().ocr(new byte[]{1}, "image/png")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void extractionUsesSharedCleaning() {
        contents.add("""
                {"nodes":[{"name":"移项","definition":"变号后移到另一边","type":"METHOD","confidence":0.9,"chunkIndexes":[0,9]}],
                 "relations":[{"fromName":"等式的性质","toName":"移项","type":"PREREQUISITE","confidence":0.8,"evidence":"依据"}]}""");
        var r = provider().extractKnowledge(new ExtractionRequest("书", List.of("1.2 移项"), List.of("原文"), List.of("等式的性质")));
        assertThat(r.nodes().get(0).chunkIndexes()).containsExactly(0);
        assertThat(r.relations()).hasSize(1);
    }

    /** 真实调用 DeepSeek：只在设置了 LEARNMAP_AI_PROVIDER=deepseek 和 LEARNMAP_AI_API_KEY 时运行（CI 里不跑） */
    @Test
    @EnabledIfEnvironmentVariable(named = "LEARNMAP_AI_PROVIDER", matches = "deepseek")
    @EnabledIfEnvironmentVariable(named = "LEARNMAP_AI_API_KEY", matches = ".+")
    void liveCall() {
        DeepSeekAiProvider ai = new DeepSeekAiProvider(new AiProperties("deepseek", System.getenv("LEARNMAP_AI_API_KEY"),
                System.getenv("LEARNMAP_AI_MODEL"), null, System.getenv("LEARNMAP_AI_BASE_URL")));
        var r = ai.extractKnowledge(new ExtractionRequest("一元一次方程", List.of("第一章 方程", "1.2 移项"),
                List.of("把方程中的某一项改变符号后，从方程的一边移到另一边，这种变形叫做移项。移项的依据是等式的性质 1。"),
                List.of("等式的性质")));
        assertThat(r.nodes()).isNotEmpty();
        assertThat(ai.generateQuestions(BRIEF, 2)).isNotEmpty();
        assertThat(ai.gradeFeynman(BRIEF, "移项就是把一项搬到等号另一边，要变号，比如 x+3=5 变成 x=5-3。").score()).isBetween(0.0, 1.0);
    }
}
