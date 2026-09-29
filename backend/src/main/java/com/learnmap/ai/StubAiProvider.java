package com.learnmap.ai;

import com.learnmap.graph.NodeType;
import com.learnmap.graph.RelationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 离线假实现，用于本地开发、测试和演示，不调用任何模型：
 * 每个小节抽成一个知识点（名称取小节标题，定义取第一句），同一章里按顺序串成前置关系。
 * 结果是确定的，但质量远不如真实模型，只用来把整条链路跑通。
 */
@Component
@ConditionalOnProperty(prefix = "learnmap.ai", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubAiProvider implements AiProvider {

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public String ocr(byte[] image, String mimeType) {
        throw new UnsupportedOperationException("当前 AI 提供方是 stub，不支持 OCR；设置 LEARNMAP_AI_PROVIDER=claude 和 LEARNMAP_AI_API_KEY 后可用（deepseek 不支持图片）");
    }

    @Override
    public ExtractionResult extractKnowledge(ExtractionRequest request) {
        if (request.chunks().isEmpty()) {
            return new ExtractionResult(List.of(), List.of());
        }
        List<String> path = request.sectionPath();
        String name = stripNumbering(path.get(path.size() - 1));
        String definition = firstSentence(request.chunks().get(0));
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < request.chunks().size(); i++) {
            all.add(i);
        }
        ExtractedNode node = new ExtractedNode(name, definition, guessType(name + definition), 0.5, all);
        List<ExtractedRelation> relations = new ArrayList<>();
        List<String> earlier = request.earlierNodeNames();
        if (!earlier.isEmpty()) {
            relations.add(new ExtractedRelation(earlier.get(earlier.size() - 1), name, RelationType.PREREQUISITE,
                    0.3, "stub：同一章内的出现顺序"));
        }
        return new ExtractionResult(List.of(node), relations);
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(NodeBrief node, int count) {
        List<GeneratedQuestion> questions = new ArrayList<>();
        if (node.definition() != null && !node.definition().isBlank()) {
            questions.add(new GeneratedQuestion("TRUE_FALSE",
                    "判断：关于「" + node.name() + "」，下面的说法是否正确？\n" + node.definition(),
                    List.of("正确", "错误"), "正确", 0.3, List.of("UNDERSTANDING", "READING")));
            // 填空：把定义里的名称挖掉，让学习者说出是哪个知识点
            String masked = node.definition().replace(node.name(), "____");
            questions.add(new GeneratedQuestion("FILL_BLANK",
                    "填空：下面说的是哪个知识点？\n" + masked, List.of(), node.name(), 0.5, List.of("RECALL")));
        }
        return questions.subList(0, Math.min(count, questions.size()));
    }

    @Override
    public FeynmanGrade gradeFeynman(NodeBrief node, String explanation) {
        // 只按关键词做粗略覆盖检查，真实评分由模型完成
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        (explanation.contains(node.name()) ? covered : missing).add("提到了「" + node.name() + "」");
        boolean hasExample = explanation.contains("例如") || explanation.contains("比如");
        (hasExample ? covered : missing).add("举了例子");
        double score = covered.size() / 2.0;
        return new FeynmanGrade(score, covered, missing, List.of(),
                List.of("能不能用一个生活中的例子再讲一遍「" + node.name() + "」？"));
    }

    @Override
    public List<String> suggestPalaceImages(List<PalaceItem> items) {
        // 固定模板，只为跑通流程；真实模型会编更生动的画面
        return items.stream().map(i -> "想象「" + i.content() + "」变成一个巨大的东西，砰地砸在「" + i.locus() + "」上，还冲你大喊它的名字")
                .toList();
    }

    @Override
    public List<Integer> suggestLinks(String card, List<String> candidates) {
        // 按两字片段的重合度粗略找相关卡片
        Set<String> a = bigrams(card);
        List<double[]> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Set<String> b = bigrams(candidates.get(i));
            Set<String> inter = new HashSet<>(a);
            inter.retainAll(b);
            Set<String> union = new HashSet<>(a);
            union.addAll(b);
            double j = union.isEmpty() ? 0 : inter.size() / (double) union.size();
            if (j >= 0.12) {
                scored.add(new double[]{i, j});
            }
        }
        return scored.stream().sorted((x, y) -> Double.compare(y[1], x[1])).limit(3).map(x -> (int) x[0]).toList();
    }

    static Set<String> bigrams(String text) {
        String t = text == null ? "" : text.replaceAll("[\\s\\p{Punct}，。、；：？！“”‘’（）【】《》]", "");
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) {
            out.add(t.substring(i, i + 2));
        }
        return out;
    }

    static String stripNumbering(String title) {
        return title.replaceFirst("^(第[一二三四五六七八九十百零〇\\d]+[章节]|\\d+(\\.\\d+)*)\\s*", "").strip();
    }

    static String firstSentence(String text) {
        String t = text.strip();
        int end = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '\n') {
                end = i + (c == '\n' ? 0 : 1);
                break;
            }
        }
        String s = end > 0 ? t.substring(0, end) : t;
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    static NodeType guessType(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("定理") || t.contains("公式") || t.contains("定律")) {
            return NodeType.PRINCIPLE;
        }
        if (t.contains("方法") || t.contains("步骤") || t.contains("法")) {
            return NodeType.METHOD;
        }
        if (t.contains("综合") || t.contains("应用")) {
            return NodeType.SYNTHESIS;
        }
        if (t.contains("术语") || t.contains("符号") || t.contains("年")) {
            return NodeType.FACT;
        }
        return NodeType.CONCEPT;
    }
}
