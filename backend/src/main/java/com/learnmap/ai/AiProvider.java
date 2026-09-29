package com.learnmap.ai;

import com.learnmap.graph.NodeType;
import com.learnmap.graph.RelationType;

import java.util.List;

/**
 * 所有用到 AI 的地方都走这一个接口：OCR、图谱抽取、出题、费曼评分、记忆宫殿画面、笔记关联。
 * 具体用哪个模型由配置 {@code learnmap.ai.provider} 决定，核心资产（图谱、证据、各 Engine）不依赖具体模型。
 */
public interface AiProvider {

    /** 提供方名称，写进日志和“AI 生成”来源标记 */
    String name();

    /** 图片类书页 / 手写讲解照片 → 文本（公式尽量保留成 LaTeX） */
    String ocr(byte[] image, String mimeType);

    /** 从一个小节的原文块里抽知识点和关系 */
    ExtractionResult extractKnowledge(ExtractionRequest request);

    /** 按节点生成能自动判分的题目 */
    List<GeneratedQuestion> generateQuestions(NodeBrief node, int count);

    /** 费曼讲解评分：对照节点要点，找讲漏、讲错、讲不清的地方，并给出追问（康奈尔笔记的总结栏也用它给反馈） */
    FeynmanGrade gradeFeynman(NodeBrief node, String explanation);

    /** 记忆宫殿：给每个“地点 + 内容”编一个夸张、有画面感的联想，顺序和数量与输入一致（编不出的返回空字符串） */
    List<String> suggestPalaceImages(List<PalaceItem> items);

    /** 卡片盒笔记：从已有卡片里找和新卡片相关的，返回下标，最多 3 个 */
    List<Integer> suggestLinks(String card, List<String> candidates);

    // ---------- 数据结构 ----------

    /**
     * @param chunks          该小节的原文块，按顺序；抽取结果用下标引用
     * @param earlierNodeNames 同一章里前面小节已经抽出的知识点，关系可以指向它们
     */
    record ExtractionRequest(String bookTitle, List<String> sectionPath, List<String> chunks, List<String> earlierNodeNames) {
    }

    record ExtractionResult(List<ExtractedNode> nodes, List<ExtractedRelation> relations) {
    }

    /** @param chunkIndexes 这个知识点出自哪几个原文块 */
    record ExtractedNode(String name, String definition, NodeType type, double confidence, List<Integer> chunkIndexes) {
    }

    /** 关系按节点名称引用；名称可以指向前面小节已经抽出的节点 */
    record ExtractedRelation(String fromName, String toName, RelationType type, double confidence, String evidence) {
    }

    record NodeBrief(String name, String definition, NodeType type, List<String> sourceExcerpts) {
    }

    /** @param abilities 考查的能力（{@link com.learnmap.question.Ability} 的名字），用于能力画像 */
    record GeneratedQuestion(String type, String stem, List<String> options, String answer, double difficulty,
                             List<String> abilities) {
    }

    record PalaceItem(String locus, String content) {
    }

    /** @param score 0~1 */
    record FeynmanGrade(double score, List<String> coveredPoints, List<String> missingPoints,
                        List<String> incorrectStatements, List<String> followUpQuestions) {
    }
}
