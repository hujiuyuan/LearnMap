package com.learnmap.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.learnmap.ai.AiPrompts.RawExtraction;
import com.learnmap.ai.AiPrompts.RawFeynman;
import com.learnmap.ai.AiPrompts.RawImages;
import com.learnmap.ai.AiPrompts.RawLinks;
import com.learnmap.ai.AiPrompts.RawQuestions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用 Claude（Anthropic 官方 Java SDK）实现 OCR、图谱抽取、出题、费曼评分、记忆宫殿画面和笔记关联。
 * learnmap.ai.provider=claude 时启用；API key 从环境变量 LEARNMAP_AI_API_KEY（或 ANTHROPIC_API_KEY）读取。
 * 用结构化输出拿到 JSON，提示词和清洗规则都在 {@link AiPrompts}，和其他模型共用。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "learnmap.ai", name = "provider", havingValue = "claude")
public class ClaudeAiProvider implements AiProvider {

    static final String DEFAULT_MODEL = "claude-opus-5";
    static final long MAX_TOKENS = 16000L;
    /** 模型拒答时由服务端自动换模型重试 */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String model;
    private final boolean fallbacks;

    public ClaudeAiProvider(AiProperties props) {
        String key = props.apiKey();
        if (key == null || key.isBlank()) {
            key = System.getenv("ANTHROPIC_API_KEY");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("learnmap.ai.provider=claude 需要 API key：请设置环境变量 LEARNMAP_AI_API_KEY");
        }
        this.client = AnthropicOkHttpClient.builder().apiKey(key).build();
        this.model = props.model() == null || props.model().isBlank() ? DEFAULT_MODEL : props.model();
        this.fallbacks = props.fallbacks() == null || props.fallbacks();
        log.info("AI 提供方：Claude，模型 {}", model);
    }

    @Override
    public String name() {
        return "claude:" + model;
    }

    @Override
    public String ocr(byte[] image, String mimeType) {
        ImageBlockParam img = ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .data(Base64.getEncoder().encodeToString(image))
                        .mediaType(Base64ImageSource.MediaType.of(mimeType == null ? "image/jpeg" : mimeType))
                        .build())
                .build();
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(img),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(AiPrompts.OCR_PROMPT).build())));
        if (fallbacks) {
            b.putAdditionalHeader("anthropic-beta", FALLBACK_BETA).putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        Message m = call(() -> client.messages().create(b.build()));
        checkStop(m.stopReason().orElse(null));
        return m.content().stream().flatMap(c -> c.text().stream()).map(t -> t.text()).collect(Collectors.joining("\n")).strip();
    }

    @Override
    public ExtractionResult extractKnowledge(ExtractionRequest request) {
        if (request.chunks().isEmpty()) {
            return new ExtractionResult(List.of(), List.of());
        }
        RawExtraction out = structured(AiPrompts.EXTRACT_SYSTEM, AiPrompts.extractUser(request), RawExtraction.class);
        return AiPrompts.clean(out, request.chunks().size(), request.earlierNodeNames());
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(NodeBrief node, int count) {
        return AiPrompts.cleanQuestions(structured(AiPrompts.QUESTION_SYSTEM, AiPrompts.questionUser(node, count), RawQuestions.class), count);
    }

    @Override
    public FeynmanGrade gradeFeynman(NodeBrief node, String explanation) {
        return AiPrompts.cleanFeynman(structured(AiPrompts.FEYNMAN_SYSTEM, AiPrompts.feynmanUser(node, explanation), RawFeynman.class));
    }

    @Override
    public List<String> suggestPalaceImages(List<PalaceItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        return AiPrompts.cleanImages(structured(AiPrompts.PALACE_SYSTEM, AiPrompts.palaceUser(items), RawImages.class), items.size());
    }

    @Override
    public List<Integer> suggestLinks(String card, List<String> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        return AiPrompts.cleanLinks(structured(AiPrompts.LINK_SYSTEM, AiPrompts.linkUser(card, candidates), RawLinks.class), candidates.size());
    }

    private <T> T structured(String system, String user, Class<T> type) {
        StructuredMessageCreateParams.Builder<T> b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(system)
                .outputConfig(type)
                .addUserMessage(user);
        if (fallbacks) {
            b.putAdditionalHeader("anthropic-beta", FALLBACK_BETA).putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        StructuredMessage<T> m = call(() -> client.messages().create(b.build()));
        checkStop(m.stopReason().orElse(null));
        return m.content().stream().flatMap(c -> c.text().stream()).map(t -> t.text()).findFirst()
                .orElseThrow(() -> new AiException("AI 没有返回结果"));
    }

    /** SDK 已经对 429 / 5xx / 网络错误自动重试；到这里还失败就转成 AiException，给出能看懂的原因 */
    private static <R> R call(java.util.function.Supplier<R> request) {
        try {
            return request.get();
        } catch (UnauthorizedException | PermissionDeniedException e) {
            throw new AiException("AI 服务拒绝访问，请检查 LEARNMAP_AI_API_KEY", e);
        } catch (RateLimitException e) {
            throw new AiException("AI 服务调用太频繁，请稍后再试", e);
        } catch (BadRequestException | NotFoundException e) {
            throw new AiException("AI 请求参数有误：" + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new AiException("AI 服务暂时不可用（" + e.statusCode() + "），请稍后再试", e);
        } catch (AnthropicException e) {
            throw new AiException("连不上 AI 服务：" + e.getMessage(), e);
        }
    }

    private static void checkStop(StopReason reason) {
        if (StopReason.REFUSAL.equals(reason)) {
            throw new AiException("AI 拒绝处理这段内容");
        }
        if (StopReason.MAX_TOKENS.equals(reason)) {
            throw new AiException("AI 输出超长被截断，请把内容拆小一些再试");
        }
    }
}
