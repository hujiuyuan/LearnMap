package com.learnmap.card;

import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.Evidence;
import com.learnmap.evidence.EvidenceRepository;
import com.learnmap.evidence.EvidenceResult;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.graph.LearnerGraphCreated;
import com.learnmap.graph.LearnerNodesAdded;
import com.learnmap.mastery.MasteryService;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 间隔重复 + 主动回忆：每个有定义的节点自动生成一张卡片（先回想再翻面），用 FSRS 排下次复习。
 * 每次复习都写成一条证据（活动类型 CARD），进入掌握度计算。
 */
@Service
@RequiredArgsConstructor
public class CardService {

    /** 卡片题目按“简单”计入掌握度：回忆定义比做题容易 */
    static final double CARD_DIFFICULTY = 0.3;

    private final ReviewCardRepository cards;
    private final KnowledgeNodeRepository nodes;
    private final EvidenceRepository evidences;
    private final MasteryService mastery;
    private final Clock clock;

    public record CardView(Long id, Long nodeId, String front, String back, CardState state, LocalDateTime dueAt) {
        static CardView of(ReviewCard c) {
            return new CardView(c.getId(), c.getNodeId(), c.getFront(), c.getBack(), c.getState(), c.getDueAt());
        }
    }

    public record DueCards(long dueCount, List<CardView> cards) {
    }

    public record ReviewResult(CardState state, LocalDateTime nextDueAt, long intervalMinutes,
                               double mastery, double confidence) {
    }

    @EventListener
    @Transactional
    public void onLearnerGraphCreated(LearnerGraphCreated event) {
        createCards(event.learnerId(), nodes.findByGraphIdOrderByOrdinal(event.graphId()));
    }

    /** 副本合并了新版图谱，新加的节点补卡片 */
    @EventListener
    @Transactional
    public void onLearnerNodesAdded(LearnerNodesAdded event) {
        createCards(event.learnerId(), nodes.findAllById(event.nodeIds()));
    }

    private void createCards(Long learnerId, List<KnowledgeNode> list) {
        LocalDateTime now = LocalDateTime.now(clock);
        for (KnowledgeNode n : list) {
            if (n.isDeleted() || n.getDefinition() == null || n.getDefinition().isBlank()) {
                continue;
            }
            ReviewCard c = new ReviewCard();
            c.setLearnerId(learnerId);
            c.setNodeId(n.getId());
            c.setFront("「" + n.getName() + "」是什么？先在心里说一遍，再翻面。");
            c.setBack(n.getDefinition());
            c.setSource("AUTO");
            c.setState(CardState.NEW);
            c.setDueAt(now);
            cards.save(c);
        }
    }

    public DueCards due(AppUser learner, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CardView> list = cards.findByLearnerIdAndDueAtLessThanEqualOrderByDueAtAsc(learner.getId(), now,
                PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream()
                .filter(c -> nodes.findById(c.getNodeId()).map(n -> !n.isDeleted()).orElse(false))
                .map(CardView::of).toList();
        return new DueCards(cards.countByLearnerIdAndDueAtLessThanEqual(learner.getId(), now), list);
    }

    @Transactional
    public ReviewResult review(AppUser actor, Long cardId, Rating rating, Integer durationSec) {
        ReviewCard c = cards.findById(cardId).orElseThrow(() -> NotFoundException.of("卡片", cardId));
        if (!c.getLearnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能复习自己的卡片");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        double elapsedDays = c.getLastReviewAt() == null ? 0
                : Duration.between(c.getLastReviewAt(), now).toMinutes() / (60.0 * 24);
        Fsrs.Result r = Fsrs.review(new Fsrs.Memory(c.getState(), c.getStability(), c.getDifficulty()), rating, elapsedDays);
        if (rating == Rating.AGAIN && c.getState() != CardState.NEW) {
            c.setLapses(c.getLapses() + 1);
        }
        c.setState(r.state());
        c.setStability(r.stability());
        c.setDifficulty(r.difficulty());
        c.setReps(c.getReps() + 1);
        c.setLastReviewAt(now);
        c.setDueAt(now.plus(r.interval()));

        KnowledgeNode node = nodes.findById(c.getNodeId()).orElseThrow(() -> NotFoundException.of("节点", c.getNodeId()));
        Evidence ev = new Evidence();
        ev.setLearnerId(actor.getId());
        ev.setNodeId(node.getId());
        ev.setActivityType(ActivityType.CARD);
        ev.setResult(switch (rating) {
            case AGAIN -> EvidenceResult.INCORRECT;
            case HARD -> EvidenceResult.PARTIAL;
            case GOOD, EASY -> EvidenceResult.CORRECT;
        });
        ev.setScore(switch (rating) {
            case AGAIN -> 0.0;
            case HARD -> 0.6;
            case GOOD, EASY -> 1.0;
        });
        ev.setDifficulty(CARD_DIFFICULTY);
        ev.setDurationSec(durationSec);
        evidences.save(ev);
        NodeMastery m = mastery.apply(ev, node);
        return new ReviewResult(c.getState(), c.getDueAt(), r.interval().toMinutes(), m.getMastery(), m.getConfidence());
    }
}
