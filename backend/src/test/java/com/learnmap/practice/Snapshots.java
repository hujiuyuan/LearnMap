package com.learnmap.practice;

import com.learnmap.graph.ItemReviewStatus;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.NodeType;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.question.Question;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 测试用：手工拼一张图谱快照 */
public class Snapshots {

    final Map<Long, KnowledgeNode> nodes = new LinkedHashMap<>();
    final Map<Long, List<Long>> prereqs = new HashMap<>();
    final Map<Long, Set<Long>> contrasts = new HashMap<>();
    final Map<Long, List<Question>> questions = new HashMap<>();
    final Map<Long, NodeMastery> mastery = new HashMap<>();
    final Map<Long, Long> chapter = new HashMap<>();

    public Snapshots node(long id, NodeType type, long chapterId) {
        KnowledgeNode n = new KnowledgeNode();
        ReflectionTestUtils.setField(n, "id", id);
        n.setName("N" + id);
        n.setNodeType(type);
        n.setOrdinal((int) id);
        n.setReviewStatus(ItemReviewStatus.CONFIRMED);
        nodes.put(id, n);
        chapter.put(id, chapterId);
        Question q = new Question();
        ReflectionTestUtils.setField(q, "id", 1000 + id);
        q.setDifficulty(0.5);
        questions.put(id, new ArrayList<>(List.of(q)));
        return this;
    }

    public Snapshots prereq(long from, long to) {
        prereqs.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
        return this;
    }

    public Snapshots contrast(long a, long b) {
        contrasts.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        contrasts.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        return this;
    }

    public Snapshots mastery(long id, double m, double c) {
        NodeMastery nm = new NodeMastery(1L, id);
        nm.setMastery(m);
        nm.setConfidence(c);
        mastery.put(id, nm);
        return this;
    }

    public Snapshots noQuestions(long id) {
        questions.remove(id);
        return this;
    }

    public LearnerGraphSnapshot build() {
        return new LearnerGraphSnapshot(1L, 1L, nodes, prereqs, contrasts, questions, mastery, chapter);
    }

    static List<PracticeItem> answered(List<Long> nodes, boolean... correct) {
        List<PracticeItem> out = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            PracticeItem it = new PracticeItem();
            it.setNodeId(nodes.get(i));
            it.setAnswered(true);
            it.setCorrect(correct.length == 1 ? correct[0] : correct[i]);
            out.add(it);
        }
        return out;
    }
}
