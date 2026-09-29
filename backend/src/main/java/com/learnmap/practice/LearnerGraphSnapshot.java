package com.learnmap.practice;

import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.NodeType;
import com.learnmap.mastery.MasteryEngine;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.question.Question;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 学习者某张图谱副本在某一时刻的只读快照：节点、前置 / 易混淆关系、每个节点能出的题、当前掌握度、所在章。
 * 各种学习方式的选题规则都基于它，便于写成纯逻辑。
 *
 * @param prereqs        节点 → 直接前置（不含被学习者标记为有误的关系）
 * @param contrasts      节点 → 易混淆节点（双向）
 * @param questionsByNode 副本节点 → 书本图谱节点上的题
 * @param chapterOf      节点 → 所在章的 section id
 */
public record LearnerGraphSnapshot(Long graphId, Long learnerId, Map<Long, KnowledgeNode> nodes,
                                   Map<Long, List<Long>> prereqs, Map<Long, Set<Long>> contrasts,
                                   Map<Long, List<Question>> questionsByNode, Map<Long, NodeMastery> mastery,
                                   Map<Long, Long> chapterOf) {

    public boolean testable(Long nodeId) {
        return !questionsByNode.getOrDefault(nodeId, List.of()).isEmpty();
    }

    public double masteryOf(Long nodeId) {
        NodeMastery m = mastery.get(nodeId);
        return m == null ? 0 : m.getMastery();
    }

    public double confidenceOf(Long nodeId) {
        NodeMastery m = mastery.get(nodeId);
        return m == null ? 0 : m.getConfidence();
    }

    public boolean mastered(Long nodeId) {
        return masteryOf(nodeId) >= MasteryEngine.MASTERED && confidenceOf(nodeId) >= MasteryEngine.CONFIDENT;
    }

    public String name(Long nodeId) {
        return nodes.get(nodeId).getName();
    }

    public NodeType type(Long nodeId) {
        return nodes.get(nodeId).getNodeType();
    }

    public List<Long> prereqsOf(Long nodeId) {
        return prereqs.getOrDefault(nodeId, List.of());
    }

    public Comparator<Long> byOrdinal() {
        return Comparator.comparingInt(id -> nodes.get(id).getOrdinal());
    }
}
