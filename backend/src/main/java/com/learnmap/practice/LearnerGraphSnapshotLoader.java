package com.learnmap.practice;

import com.learnmap.book.BookSection;
import com.learnmap.book.BookSectionRepository;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.graph.NodeRelation;
import com.learnmap.graph.NodeRelationRepository;
import com.learnmap.graph.RelationType;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.mastery.NodeMasteryRepository;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LearnerGraphSnapshotLoader {

    private final KnowledgeNodeRepository nodes;
    private final NodeRelationRepository relations;
    private final QuestionRepository questions;
    private final NodeMasteryRepository masteries;
    private final BookSectionRepository sections;

    public LearnerGraphSnapshot load(KnowledgeGraph graph) {
        Map<Long, KnowledgeNode> ns = nodes.findByGraphIdOrderByOrdinal(graph.getId()).stream()
                .filter(n -> !n.isDeleted())
                .collect(Collectors.toMap(KnowledgeNode::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Map<Long, List<Long>> prereqs = new HashMap<>();
        Map<Long, Set<Long>> contrasts = new HashMap<>();
        for (NodeRelation r : relations.findByGraphId(graph.getId())) {
            if (r.isDisputed() || !ns.containsKey(r.getFromNodeId()) || !ns.containsKey(r.getToNodeId())) {
                continue;
            }
            if (r.getRelationType() == RelationType.PREREQUISITE) {
                prereqs.computeIfAbsent(r.getToNodeId(), k -> new ArrayList<>()).add(r.getFromNodeId());
            } else if (r.getRelationType() == RelationType.CONTRASTS) {
                contrasts.computeIfAbsent(r.getFromNodeId(), k -> new HashSet<>()).add(r.getToNodeId());
                contrasts.computeIfAbsent(r.getToNodeId(), k -> new HashSet<>()).add(r.getFromNodeId());
            }
        }

        // 题目挂在书本图谱节点上：副本节点按 origin 找回去；MASTER 图谱本身直接用自己的 id
        Map<Long, Long> byOrigin = ns.values().stream()
                .collect(Collectors.toMap(n -> n.getOriginNodeId() != null ? n.getOriginNodeId() : n.getId(),
                        KnowledgeNode::getId, (a, b) -> a));
        Map<Long, List<Question>> qs = questions.findByNodeIdIn(byOrigin.keySet()).stream()
                .collect(Collectors.groupingBy(q -> byOrigin.get(q.getNodeId())));

        Map<Long, NodeMastery> mastery = graph.getOwnerLearnerId() == null ? Map.of()
                : masteries.findByLearnerIdAndNodeIdIn(graph.getOwnerLearnerId(), ns.keySet()).stream()
                .collect(Collectors.toMap(NodeMastery::getNodeId, Function.identity()));

        Map<Long, BookSection> sectionById = sections.findByBookIdOrderByOrdinal(graph.getBookId()).stream()
                .collect(Collectors.toMap(BookSection::getId, Function.identity()));
        Map<Long, Long> chapterOf = new HashMap<>();
        for (KnowledgeNode n : ns.values()) {
            BookSection s = n.getSectionId() == null ? null : sectionById.get(n.getSectionId());
            while (s != null && s.getParentId() != null && sectionById.containsKey(s.getParentId())) {
                s = sectionById.get(s.getParentId());
            }
            chapterOf.put(n.getId(), s == null ? 0L : s.getId());
        }
        return new LearnerGraphSnapshot(graph.getId(), graph.getOwnerLearnerId(), ns, prereqs, contrasts, qs,
                mastery, chapterOf);
    }
}
