package com.learnmap.mastery;

import com.learnmap.evidence.Evidence;
import com.learnmap.graph.ItemReviewStatus;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.mastery.MasteryEngine.State;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Mastery Engine 只读证据，不关心证据来自哪种学习方式。 */
@Service
@RequiredArgsConstructor
public class MasteryService {

    private final NodeMasteryRepository masteries;

    @Transactional
    public NodeMastery apply(Evidence evidence, KnowledgeNode node) {
        NodeMastery row = masteries.findByLearnerIdAndNodeId(evidence.getLearnerId(), node.getId())
                .orElseGet(() -> new NodeMastery(evidence.getLearnerId(), node.getId()));
        State prev = new State(row.getMastery(), row.getConfidence(), row.getEvidenceCount());
        State next = MasteryEngine.update(prev, evidence.getScore(), evidence.getDifficulty(),
                evidence.getSelfConfidence(), node.getReviewStatus() == ItemReviewStatus.CONFIRMED);
        row.setMastery(next.mastery());
        row.setConfidence(next.confidence());
        row.setEvidenceCount(next.evidenceCount());
        row.setUpdatedAt(LocalDateTime.now());
        return masteries.save(row);
    }
}
