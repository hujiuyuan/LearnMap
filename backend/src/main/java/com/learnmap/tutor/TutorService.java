package com.learnmap.tutor;

import com.learnmap.book.Book;
import com.learnmap.book.BookRepository;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.ErrorType;
import com.learnmap.evidence.Evidence;
import com.learnmap.evidence.EvidenceRepository;
import com.learnmap.evidence.EvidenceResult;
import com.learnmap.feynman.FeynmanAttemptRepository;
import com.learnmap.flag.FlagKind;
import com.learnmap.flag.FlagService;
import com.learnmap.flag.FlagService.FlagView;
import com.learnmap.flag.LearnerFlag;
import com.learnmap.flag.LearnerFlagRepository;
import com.learnmap.graph.GraphService;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeGraphRepository;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.graph.NodeType;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.plan.StudyGoal;
import com.learnmap.plan.StudyGoalRepository;
import com.learnmap.practice.LearnerGraphSnapshot;
import com.learnmap.practice.LearnerGraphSnapshotLoader;
import com.learnmap.profile.LearnerProfile;
import com.learnmap.profile.NodeStatus;
import com.learnmap.profile.ProfileService;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import com.learnmap.user.AppUser;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 辅导者 Dashboard：名下学习者一览、单个节点的“为什么不会”追溯 */
@Service
@RequiredArgsConstructor
public class TutorService {

    static final int TRACE_DEPTH = 3;

    private final UserService users;
    private final ProfileService profiles;
    private final EvidenceRepository evidences;
    private final FeynmanAttemptRepository feynman;
    private final LearnerFlagRepository flagRepo;
    private final FlagService flags;
    private final StudyGoalRepository goals;
    private final GraphService graphs;
    private final KnowledgeNodeRepository nodes;
    private final LearnerGraphSnapshotLoader snapshots;
    private final QuestionRepository questions;
    private final KnowledgeGraphRepository graphRepo;
    private final BookRepository books;

    public record LearnerCard(Long learnerId, String name, int lit, int total, int weak, int streak, int activeDays14,
                              Double planCompletionRate, String planWarning, LocalDateTime lastActiveAt,
                              int openFlags, int feynmanToReview, List<String> alerts) {
    }

    public record TraceEvidence(ActivityType activity, EvidenceResult result, double score, String questionStem,
                                ErrorType errorType, LocalDateTime at) {
    }

    public record TracePrereq(Long nodeId, String name, NodeType type, Long parentId, int depth, Double mastery,
                              NodeStatus status) {
    }

    /**
     * @param rootCauses 薄弱的前置里，自己的前置都没问题的那些：最可能的根因
     */
    public record Trace(Long learnerId, Long nodeId, String name, NodeType type, String definition, Double mastery,
                        Double confidence, NodeStatus status, List<TraceEvidence> recent, List<TracePrereq> prerequisites,
                        List<String> rootCauses, String explanation, List<FlagView> flags) {
    }

    /** 班级热力图：学习者 × 章，格子是该章的平均掌握度（没这本书的为空） */
    public record Heatmap(List<String> columns, List<HeatRow> rows) {
    }

    public record HeatRow(Long learnerId, String name, List<Double> values) {
    }

    @Transactional(readOnly = true)
    public Heatmap heatmap(AppUser tutor) {
        if (tutor.getRole() != Role.TUTOR) {
            throw new ForbiddenException("只有辅导者有班级热力图");
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        List<Map<String, Double>> cells = new ArrayList<>();
        List<AppUser> learners = users.learnersOf(tutor.getId());
        for (AppUser l : learners) {
            Map<String, Double> row = new HashMap<>();
            for (LearnerProfile.ChapterStat ch : profiles.compute(l.getId()).knowledge().chapters()) {
                String book = graphRepo.findById(ch.graphId()).flatMap(g -> books.findById(g.getBookId()))
                        .map(Book::getTitle).orElse("");
                String col = book + " · " + ch.title();
                columns.putIfAbsent(col, columns.size());
                row.put(col, ch.avgMastery());
            }
            cells.add(row);
        }
        List<String> cols = new ArrayList<>(columns.keySet());
        List<HeatRow> rows = new ArrayList<>();
        for (int i = 0; i < learners.size(); i++) {
            Map<String, Double> row = cells.get(i);
            rows.add(new HeatRow(learners.get(i).getId(), learners.get(i).getDisplayName(), cols.stream().map(row::get).toList()));
        }
        return new Heatmap(cols, rows);
    }

    @Transactional(readOnly = true)
    public List<LearnerCard> dashboard(AppUser tutor) {
        if (tutor.getRole() != Role.TUTOR) {
            throw new ForbiddenException("只有辅导者有 Dashboard");
        }
        List<LearnerCard> out = new ArrayList<>();
        for (AppUser l : users.learnersOf(tutor.getId())) {
            LearnerProfile p = profiles.compute(l.getId());
            StudyGoal goal = goals.findFirstByLearnerIdAndActiveTrueOrderByIdDesc(l.getId()).orElse(null);
            int openFlags = flagRepo.findByLearnerIdAndResolvedFalse(l.getId()).size();
            int toReview = (int) feynman.countByLearnerIdAndNeedsReviewTrue(l.getId());
            LocalDateTime last = evidences.findFirstByLearnerIdOrderByIdDesc(l.getId()).map(Evidence::getCreatedAt).orElse(null);
            List<String> alerts = new ArrayList<>();
            if (goal != null && goal.getWarning() != null) {
                alerts.add(goal.getWarning());
            }
            if (p.behavior().planCompletionRate() != null && p.behavior().planCompletionRate() < 0.5) {
                alerts.add("计划完成率 " + Math.round(p.behavior().planCompletionRate() * 100) + "%，计划可能太重");
            }
            if (toReview > 0) {
                alerts.add(toReview + " 次费曼讲解等你复核");
            }
            long stuck = flagRepo.findByLearnerIdAndResolvedFalse(l.getId()).stream().filter(f -> f.getKind() == FlagKind.STUCK).count();
            if (stuck > 0) {
                alerts.add(stuck + " 个知识点补了两轮还卡住，需要你帮忙");
            }
            if (last != null && last.isBefore(LocalDateTime.now().minusDays(3))) {
                alerts.add("已经 3 天以上没学习了");
            }
            out.add(new LearnerCard(l.getId(), l.getDisplayName(), p.knowledge().mastered(), p.knowledge().total(),
                    p.knowledge().weak(), p.behavior().streak(), p.behavior().activeDays14(),
                    p.behavior().planCompletionRate(), goal == null ? null : goal.getWarning(), last, openFlags, toReview, alerts));
        }
        return out;
    }

    /** 某个节点为什么不会：最近的作答、前置链上各节点的掌握情况、最可能的根因 */
    @Transactional(readOnly = true)
    public Trace trace(AppUser actor, Long learnerId, Long nodeId) {
        users.checkCanView(actor, learnerId);
        KnowledgeNode node = nodes.findById(nodeId).orElseThrow(() -> NotFoundException.of("节点", nodeId));
        KnowledgeGraph g = graphs.require(node.getGraphId());
        if (!Objects.equals(g.getOwnerLearnerId(), learnerId)) {
            throw new IllegalArgumentException("这个节点不在该学习者的图谱上");
        }
        LearnerGraphSnapshot snap = snapshots.load(g);

        List<Evidence> evs = evidences.findByLearnerIdAndNodeIdOrderByIdAsc(learnerId, nodeId);
        List<TraceEvidence> recent = new ArrayList<>();
        for (int i = evs.size() - 1; i >= 0 && recent.size() < 10; i--) {
            Evidence e = evs.get(i);
            String stem = e.getQuestionId() == null ? null : questions.findById(e.getQuestionId()).map(Question::getStem).orElse(null);
            recent.add(new TraceEvidence(e.getActivityType(), e.getResult(), e.getScore(), stem, e.getErrorType(), e.getCreatedAt()));
        }

        List<TracePrereq> prereqs = new ArrayList<>();
        Set<Long> seen = new HashSet<>(Set.of(nodeId));
        collect(snap, nodeId, 1, prereqs, seen);
        List<String> roots = prereqs.stream()
                .filter(p -> p.status() == NodeStatus.WEAK)
                .filter(p -> !hasWeakBelow(snap, p.nodeId(), new HashSet<>()))
                .map(TracePrereq::name).distinct().toList();

        NodeMastery m = snap.mastery().get(nodeId);
        NodeStatus st = NodeStatus.of(m);
        String explanation;
        if (!roots.isEmpty()) {
            explanation = "往下追到「" + String.join("、", roots) + "」也疑似薄弱，再往下的前置没有薄弱的，建议先补这里，再逐层回到「" + node.getName() + "」";
        } else if (prereqs.stream().anyMatch(p -> p.status() == NodeStatus.UNTOUCHED || p.status() == NodeStatus.UNSURE)) {
            explanation = "有前置还没测过或数据不足，先做一次第一性原理补缺，看看问题出在哪一层";
        } else if (st == NodeStatus.WEAK) {
            explanation = "前置都没问题，问题可能就在「" + node.getName() + "」本身，适合做刻意练习";
        } else {
            explanation = "这个知识点目前没有明显问题";
        }
        return new Trace(learnerId, nodeId, node.getName(), node.getNodeType(), node.getDefinition(),
                m == null ? null : m.getMastery(), m == null ? null : m.getConfidence(), st, recent, prereqs, roots,
                explanation, flags.of(actor, learnerId).stream().filter(f -> nodeId.equals(f.nodeId())).toList());
    }

    private void collect(LearnerGraphSnapshot snap, Long node, int depth, List<TracePrereq> out, Set<Long> seen) {
        if (depth > TRACE_DEPTH) {
            return;
        }
        for (Long p : snap.prereqsOf(node)) {
            if (!seen.add(p)) {
                continue;
            }
            NodeMastery m = snap.mastery().get(p);
            out.add(new TracePrereq(p, snap.name(p), snap.type(p), node, depth, m == null ? null : m.getMastery(), NodeStatus.of(m)));
            collect(snap, p, depth + 1, out, seen);
        }
    }

    /** 这个节点往下（所有层）的前置里还有没有薄弱的 */
    private static boolean hasWeakBelow(LearnerGraphSnapshot snap, Long node, Set<Long> seen) {
        for (Long p : snap.prereqsOf(node)) {
            if (seen.add(p) && (NodeStatus.of(snap.mastery().get(p)) == NodeStatus.WEAK || hasWeakBelow(snap, p, seen))) {
                return true;
            }
        }
        return false;
    }
}
