package com.learnmap.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.ai.AiPrompts.RawExtraction;
import com.learnmap.ai.AiPrompts.RawFeynman;
import com.learnmap.ai.AiPrompts.RawImages;
import com.learnmap.ai.AiPrompts.RawLinks;
import com.learnmap.ai.AiPrompts.RawQuestions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 用 DeepSeek 实现图谱抽取、出题、费曼评分、记忆宫殿画面和笔记关联。learnmap.ai.provider=deepseek 时启用。
 * DeepSeek 的接口和 OpenAI 的 chat/completions 兼容，没有官方 Java SDK，这里直接发 HTTP；
 * 用 JSON 输出模式（response_format=json_object），提示词里给出 JSON 示例，结果同样经过 {@link AiPrompts} 清洗。
 * DeepSeek 的对话接口不收图片，所以不支持 OCR：照片、扫描版 PDF、手写讲解会提示不支持。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "learnmap.ai", name = "provider", havingValue = "deepseek")
public class DeepSeekAiProvider implements AiProvider {

    static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    static final String DEFAULT_MODEL = "deepseek-chat";
    static final int MAX_TOKENS = 8000;
    /** JSON 模式偶尔返回空内容，重试几次 */
    static final int EMPTY_RETRIES = 2;
    static final int HTTP_RETRIES = 2;

    private final RestClient http;
    private final String model;
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DeepSeekAiProvider(AiProperties props) {
        String key = props.apiKey();
        if (key == null || key.isBlank()) {
            key = System.getenv("DEEPSEEK_API_KEY");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("learnmap.ai.provider=deepseek 需要 API key：请设置环境变量 LEARNMAP_AI_API_KEY");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(20));
        factory.setReadTimeout(Duration.ofMinutes(5));
        String base = props.baseUrl() == null || props.baseUrl().isBlank() ? DEFAULT_BASE_URL : props.baseUrl().replaceAll("/+$", "");
        this.http = RestClient.builder().baseUrl(base).requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + key).build();
        this.model = props.model() == null || props.model().isBlank() ? DEFAULT_MODEL : props.model();
        log.info("AI 提供方：DeepSeek（{}），模型 {}", base, model);
    }

    @Override
    public String name() {
        return "deepseek:" + model;
    }

    @Override
    public String ocr(byte[] image, String mimeType) {
        throw new UnsupportedOperationException("DeepSeek 不支持识别图片里的文字；照片和扫描版 PDF 需要换成支持图片的 AI 提供方（如 claude）");
    }

    @Override
    public ExtractionResult extractKnowledge(ExtractionRequest request) {
        if (request.chunks().isEmpty()) {
            return new ExtractionResult(List.of(), List.of());
        }
        RawExtraction out = ask(AiPrompts.EXTRACT_SYSTEM, AiPrompts.EXTRACT_JSON_EXAMPLE, AiPrompts.extractUser(request), RawExtraction.class);
        return AiPrompts.clean(out, request.chunks().size(), request.earlierNodeNames());
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(NodeBrief node, int count) {
        return AiPrompts.cleanQuestions(ask(AiPrompts.QUESTION_SYSTEM, AiPrompts.QUESTION_JSON_EXAMPLE,
                AiPrompts.questionUser(node, count), RawQuestions.class), count);
    }

    @Override
    public FeynmanGrade gradeFeynman(NodeBrief node, String explanation) {
        return AiPrompts.cleanFeynman(ask(AiPrompts.FEYNMAN_SYSTEM, AiPrompts.FEYNMAN_JSON_EXAMPLE,
                AiPrompts.feynmanUser(node, explanation), RawFeynman.class));
    }

    @Override
    public List<String> suggestPalaceImages(List<PalaceItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        return AiPrompts.cleanImages(ask(AiPrompts.PALACE_SYSTEM, AiPrompts.PALACE_JSON_EXAMPLE,
                AiPrompts.palaceUser(items), RawImages.class), items.size());
    }

    @Override
    public List<Integer> suggestLinks(String card, List<String> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return AiPrompts.cleanLinks(ask(AiPrompts.LINK_SYSTEM, AiPrompts.LINK_JSON_EXAMPLE,
                AiPrompts.linkUser(card, candidates), RawLinks.class), candidates.size());
    }

    /** JSON 模式要求提示词里出现 “json” 并给出格式示例 */
    static String jsonSystem(String system, String example) {
        return system + "\n只输出一个 json 对象，不要输出别的文字。格式示例：\n" + example;
    }

    private <T> T ask(String system, String example, String user, Class<T> type) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", jsonSystem(system, example)),
                        Map.of("role", "user", "content", user)));
        for (int attempt = 0; ; attempt++) {
            JsonNode res = post(body);
            JsonNode choice = res.path("choices").path(0);
            String finish = choice.path("finish_reason").asText("");
            String content = choice.path("message").path("content").asText("");
            if ("length".equals(finish)) {
                throw new AiException("AI 输出超长被截断，请把内容拆小一些再试");
            }
            if ("content_filter".equals(finish)) {
                throw new AiException("AI 拒绝处理这段内容");
            }
            if (!content.isBlank()) {
                return parse(content, type);
            }
            if (attempt >= EMPTY_RETRIES) {
                throw new AiException("AI 没有返回结果");
            }
        }
    }

    static <T> T parse(ObjectMapper json, String content, Class<T> type) {
        String s = content.strip();
        // 个别情况下会包一层 ```json ... ```
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
        }
        try {
            return json.readValue(s, type);
        } catch (Exception e) {
            throw new AiException("AI 返回的格式不对", e);
        }
    }

    private <T> T parse(String content, Class<T> type) {
        return parse(json, content, type);
    }

    /** 429、5xx 和网络错误重试两次，其余转成 AiException */
    private JsonNode post(Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            try {
                return http.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON).body(body)
                        .retrieve().body(JsonNode.class);
            } catch (RestClientResponseException e) {
                HttpStatusCode code = e.getStatusCode();
                boolean retryable = code.value() == 429 || code.is5xxServerError();
                if (retryable && attempt < HTTP_RETRIES) {
                    sleep(attempt);
                    continue;
                }
                throw switch (code.value()) {
                    case 401, 403 -> new AiException("AI 服务拒绝访问，请检查 LEARNMAP_AI_API_KEY", e);
                    case 402 -> new AiException("DeepSeek 账户余额不足", e);
                    case 429 -> new AiException("AI 服务调用太频繁，请稍后再试", e);
                    case 400, 404, 422 -> new AiException("AI 请求参数有误：" + e.getResponseBodyAsString(), e);
                    default -> new AiException("AI 服务暂时不可用（" + code.value() + "），请稍后再试", e);
                };
            } catch (ResourceAccessException e) {
                if (attempt < HTTP_RETRIES) {
                    sleep(attempt);
                    continue;
                }
                throw new AiException("连不上 AI 服务：" + e.getMessage(), e);
            }
        }
    }

    private static void sleep(int attempt) {
        try {
            Thread.sleep(1000L << attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AiException("AI 请求被中断");
        }
    }
}
