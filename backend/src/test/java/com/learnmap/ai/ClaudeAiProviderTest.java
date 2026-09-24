package com.learnmap.ai;

import com.anthropic.models.messages.MessageCreateParams;
import com.learnmap.ai.AiProvider.ExtractionRequest;
import com.learnmap.ai.AiProvider.ExtractionResult;
import com.learnmap.ai.AiProvider.FeynmanGrade;
import com.learnmap.ai.AiProvider.GeneratedQuestion;
import com.learnmap.ai.AiProvider.NodeBrief;
import com.learnmap.ai.ClaudeAiProvider.ClaudeExtraction;
import com.learnmap.ai.ClaudeAiProvider.ClaudeNode;
import com.learnmap.ai.ClaudeAiProvider.ClaudeQuestion;
import com.learnmap.ai.ClaudeAiProvider.ClaudeQuestions;
import com.learnmap.ai.ClaudeAiProvider.ClaudeRelation;
import com.learnmap.graph.NodeType;
import com.learnmap.graph.RelationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ClaudeAiProviderTest {

    @Test
    void schemasCanBeDerivedFromResultRecords() {
        for (Class<?> type : List.of(ClaudeExtraction.class, ClaudeQuestions.class, ClaudeAiProvider.ClaudeFeynman.class)) {
            assertThatCode(() -> MessageCreateParams.builder().model(ClaudeAiProvider.DEFAULT_MODEL).maxTokens(10L)
                    .outputConfig(type).addUserMessage("x").build()).doesNotThrowAnyException();
        }
    }

    @Test
    void extractionIsCleaned() {
        ClaudeExtraction raw = new ClaudeExtraction(List.of(
                new ClaudeNode("移项", "把一项变号后移到等号另一边", "method", 1.4, List.of(0, 5, 0)),
                new ClaudeNode("移项", "重复", "METHOD", 0.9, List.of(0)),
                new ClaudeNode("等式的性质", "等式两边同加一个数仍相等", "奇怪类型", 0.8, List.of()),
                new ClaudeNode(" ", "空名字", "FACT", 0.5, List.of(0))),
                List.of(
                        new ClaudeRelation("等式的性质", "移项", "PREREQUISITE", 0.8, "移项的依据是等式的性质"),
                        new ClaudeRelation("方程", "移项", "PREREQUISITE", 0.7, "前面小节"),
                        new ClaudeRelation("移项", "不存在", "RELATED", 0.5, ""),
                        new ClaudeRelation("移项", "移项", "RELATED", 0.5, "")));
        ExtractionResult r = ClaudeAiProvider.clean(raw, 2, List.of("方程"));

        assertThat(r.nodes()).extracting(AiProvider.ExtractedNode::name).containsExactly("移项", "等式的性质");
        assertThat(r.nodes().get(0).type()).isEqualTo(NodeType.METHOD);
        assertThat(r.nodes().get(0).confidence()).isEqualTo(1.0);
        assertThat(r.nodes().get(0).chunkIndexes()).containsExactly(0);
        assertThat(r.nodes().get(1).type()).isEqualTo(NodeType.CONCEPT);
        assertThat(r.nodes().get(1).chunkIndexes()).containsExactly(0);
        assertThat(r.relations()).extracting(AiProvider.ExtractedRelation::fromName).containsExactly("等式的性质", "方程");
        assertThat(r.relations().get(0).type()).isEqualTo(RelationType.PREREQUISITE);
    }

    @Test
    void sameNameAsEarlierSectionIsKeptSoSourcesMerge() {
        ClaudeExtraction raw = new ClaudeExtraction(List.of(new ClaudeNode("方程", "含未知数的等式", "CONCEPT", 0.9, List.of(0))), List.of());
        assertThat(ClaudeAiProvider.clean(raw, 1, List.of("方程")).nodes()).hasSize(1);
    }

    @Test
    void ungradableQuestionsAreDropped() {
        ClaudeQuestions raw = new ClaudeQuestions(List.of(
                new ClaudeQuestion("SINGLE_CHOICE", "2x=4，x=？", List.of("1", "2", "3", "4"), "2", 0.3, List.of("CALCULATION", "瞎写")),
                new ClaudeQuestion("SINGLE_CHOICE", "答案不在选项里", List.of("1", "2"), "5", 0.3, List.of()),
                new ClaudeQuestion("TRUE_FALSE", "移项要变号", List.of("对", "错"), "正确", 0.3, List.of("UNDERSTANDING")),
                new ClaudeQuestion("ESSAY", "论述题判不了分", List.of(), "随便", 0.5, List.of()),
                new ClaudeQuestion("fill_blank", "x+1=3，x=____", null, " 2 ", 1.5, null)));
        List<GeneratedQuestion> qs = ClaudeAiProvider.cleanQuestions(raw, 5);

        assertThat(qs).extracting(GeneratedQuestion::type).containsExactly("SINGLE_CHOICE", "TRUE_FALSE", "FILL_BLANK");
        assertThat(qs.get(0).abilities()).containsExactly("CALCULATION");
        assertThat(qs.get(1).options()).containsExactly("正确", "错误");
        assertThat(qs.get(2).answer()).isEqualTo("2");
        assertThat(qs.get(2).options()).isEmpty();
        assertThat(qs.get(2).difficulty()).isEqualTo(1.0);
        assertThat(ClaudeAiProvider.cleanQuestions(raw, 1)).hasSize(1);
    }

    /** 真实调用 Claude：只在设置了 LEARNMAP_AI_API_KEY 时运行（CI 里不跑） */
    @Test
    @EnabledIfEnvironmentVariable(named = "LEARNMAP_AI_API_KEY", matches = ".+")
    void liveCall() {
        ClaudeAiProvider ai = new ClaudeAiProvider(new AiProperties("claude", System.getenv("LEARNMAP_AI_API_KEY"),
                System.getenv("LEARNMAP_AI_MODEL"), true));
        ExtractionResult r = ai.extractKnowledge(new ExtractionRequest("一元一次方程", List.of("第一章 方程", "1.2 移项"),
                List.of("把方程中的某一项改变符号后，从方程的一边移到另一边，这种变形叫做移项。移项的依据是等式的性质 1。"),
                List.of("等式的性质")));
        assertThat(r.nodes()).isNotEmpty();
        NodeBrief brief = new NodeBrief(r.nodes().get(0).name(), r.nodes().get(0).definition(), r.nodes().get(0).type(), List.of());
        assertThat(ai.generateQuestions(brief, 2)).isNotEmpty();
        FeynmanGrade g = ai.gradeFeynman(brief, "移项就是把一项从等号一边搬到另一边，搬过去要变号，比如 x+3=5 变成 x=5-3。");
        assertThat(g.score()).isBetween(0.0, 1.0);
    }
}
