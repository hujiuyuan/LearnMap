package com.learnmap.diagnostic;

import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.diagnostic.DiagnosticSession.Status;
import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.AnswerService;
import com.learnmap.evidence.AnswerService.AnswerRequest;
import com.learnmap.evidence.AnswerService.AnswerResult;
import com.learnmap.evidence.SelfConfidence;
import com.learnmap.graph.GraphKind;
import com.learnmap.graph.GraphService;
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
import com.learnmap.question.QuestionType;
import com.learnmap.user.AppUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 入门诊断：选书后第一次学习前，三轮测出“已会 / 不会 / 疑似根因”，决定计划起点。
 * 每道题都通过 {@link AnswerService} 写成证据（活动类型 DIAGNOSTIC），所以诊断结束时知识画像已经有了第一版。
 */
@Service
@RequiredArgsConstructor
public class DiagnosticService {

    private final DiagnosticSessionRepository sessions;
    private final DiagnosticItemRepository items;
    private final GraphService graphs;
    private final KnowledgeNodeRepository nodes;
    private final NodeRelationRepository relations;
    private final QuestionRepository questions;
    private final NodeMasteryRepository masteries;
    private final AnswerService answers;
    private final UserService users;
    private final Clock clock;

    // ---------- 视图 ----------

    /** 题目不带答案，也不告诉学习者考的是哪个知识点 */
    public record ItemView(Long id, int round, Long questionId, QuestionType type, String stem, String optionsJson) {
    }

    public record SessionView(Long id, Long graphId, int round, Status status, int answeredInRound, int totalInRound,
                              ItemView next) {
    }

    public record NodeFinding(Long nodeId, String name, Double mastery, int impact) {
    }

    /**
     * @param rootCauses 疑似根因：验证轮仍然做错，建议优先修复
     * @param toObserve  验证轮做对了，可能是粗心或蒙错，建议之后再验证
     * @param affected   做错了，但它的前置也有问题，先补前置
     * @param good       做对的节点
     * @param untested   这次没有测到的节点数
     */
    public record Report(Long sessionId, Status status, List<NodeFinding> rootCauses, List<NodeFinding> toObserve,
                         List<NodeFinding> affected, List<NodeFinding> good, int untested) {
    }

    public record AnswerOutcome(boolean correct, String expectedAnswer, SessionView session) {
    }

    // ---------- 流程 ----------

    @Transactional
    public SessionView start(AppUser actor, Long graphId) {
        KnowledgeGraph g = graphs.require(graphId);
        if (g.getKind() != GraphKind.LEARNER_COPY || !actor.getId().equals(g.getOwnerLearnerId())) {
            throw new ForbiddenException("只能在自己的图谱上做诊断");
        }
        var existing = sessions.findFirstByLearnerIdAndGraphIdAndStatusOrderByIdDesc(actor.getId(), graphId, Status.IN_PROGRESS);
        if (existing.isPresent()) {
            return view(existing.get());
        }
        DiagnosticSession s = new DiagnosticSession();
        s.setLearnerId(actor.getId());
        s.setGraphId(graphId);
        s.setCurrentRound(1);
        s.setStatus(Status.IN_PROGRESS);
        s = sessions.save(s);

        Context ctx = context(s);
        List<Long> ordered = ctx.nodes.values().stream()
                .filter(n -> ctx.testable.contains(n.getId()))
                .sorted(Comparator.comparingInt(KnowledgeNode::getOrdinal))
                .map(KnowledgeNode::getId).toList();
        addItems(s, 1, DiagnosticPlanner.breadth(ordered, DiagnosticPlanner.BREADTH_MAX), ctx);
        advanceIfRoundDone(s, ctx);
        return view(s);
    }

    public SessionView get(AppUser actor, Long sessionId) {
        return view(requireOwn(actor, sessionId));
    }

    @Transactional
    public AnswerOutcome answer(AppUser actor, Long sessionId, Long itemId, String answer,
                                SelfConfidence selfConfidence, Integer durationSec) {
        DiagnosticSession s = requireOwn(actor, sessionId);
        if (s.getStatus() != Status.IN_PROGRESS) {
            throw new IllegalStateException("这次诊断已经结束");
        }
        DiagnosticItem item = items.findById(itemId).filter(i -> i.getSessionId().equals(s.getId()))
                .orElseThrow(() -> NotFoundException.of("诊断题", itemId));
        if (item.isAnswered() || item.getRoundNo() != s.getCurrentRound()) {
            throw new IllegalStateException("这道题已经答过了");
        }
        AnswerResult r = answers.record(actor, new AnswerRequest(item.getQuestionId(), ActivityType.DIAGNOSTIC, answer,
                null, null, durationSec, selfConfidence, null));
        item.setAnswered(true);
        // 自评“不会”却做对，诊断上按“不会”处理（掌握度里已经按蒙对打了折）
        item.setCorrect(r.correct() && selfConfidence != SelfConfidence.DONT_KNOW);
        item.setAnsweredAt(LocalDateTime.now(clock));
        items.save(item);
        advanceIfRoundDone(s, context(s));
        return new AnswerOutcome(r.correct(), r.expectedAnswer(), view(s));
    }

    public Report report(AppUser actor, Long sessionId) {
        DiagnosticSession s = sessions.findById(sessionId).orElseThrow(() -> NotFoundException.of("诊断", sessionId));
        users.checkCanView(actor, s.getLearnerId());
        Context ctx = context(s);
        List<DiagnosticItem> all = items.findBySessionIdOrderByRoundNoAscOrdinalAsc(s.getId());
        Map<Long, Boolean> before = latestResults(all, 2);
        Map<Long, Boolean> verify = latestResults(all.stream().filter(i -> i.getRoundNo() == 3).toList(), 3);
        List<Long> suspects = DiagnosticPlanner.suspects(before, ctx.prereqs, Integer.MAX_VALUE);
        Map<Long, List<Long>> dependents = DiagnosticPlanner.invert(ctx.prereqs);
        Map<Long, NodeMastery> mastery = masteries.findByLearnerIdAndNodeIdIn(s.getLearnerId(), ctx.nodes.keySet()).stream()
                .collect(Collectors.toMap(NodeMastery::getNodeId, Function.identity()));
        Function<Long, NodeFinding> finding = id -> new NodeFinding(id, ctx.nodes.get(id).getName(),
                mastery.containsKey(id) ? mastery.get(id).getMastery() : null, DiagnosticPlanner.impact(id, dependents));

        List<NodeFinding> rootCauses = new ArrayList<>();
        List<NodeFinding> toObserve = new ArrayList<>();
        for (Long id : suspects) {
            (Boolean.TRUE.equals(verify.get(id)) ? toObserve : rootCauses).add(finding.apply(id));
        }
        Set<Long> suspectSet = new HashSet<>(suspects);
        List<NodeFinding> affected = before.entrySet().stream()
                .filter(e -> !e.getValue() && !suspectSet.contains(e.getKey()))
                .map(e -> finding.apply(e.getKey())).toList();
        List<NodeFinding> good = before.entrySet().stream().filter(Map.Entry::getValue)
                .map(e -> finding.apply(e.getKey())).toList();
        int untested = (int) ctx.nodes.keySet().stream().filter(id -> !before.containsKey(id)).count();
        return new Report(s.getId(), s.getStatus(), rootCauses, toObserve, affected, good, untested);
    }

    // ---------- 内部 ----------

    private void advanceIfRoundDone(DiagnosticSession s, Context ctx) {
        while (s.getStatus() == Status.IN_PROGRESS) {
            List<DiagnosticItem> all = items.findBySessionIdOrderByRoundNoAscOrdinalAsc(s.getId());
            boolean pending = all.stream().anyMatch(i -> i.getRoundNo() == s.getCurrentRound() && !i.isAnswered());
            if (pending) {
                return;
            }
            if (s.getCurrentRound() >= 3) {
                complete(s);
                return;
            }
            int next = s.getCurrentRound() + 1;
            Map<Long, Boolean> results = latestResults(all, 2);
            List<Long> picked = next == 2
                    ? DiagnosticPlanner.targeted(results.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).toList(),
                    ctx.prereqs, results.keySet(), ctx.testable, DiagnosticPlanner.TARGETED_MAX)
                    : DiagnosticPlanner.suspects(results, ctx.prereqs, DiagnosticPlanner.VERIFY_MAX);
            s.setCurrentRound(next);
            addItems(s, next, picked, ctx);
            sessions.save(s);
        }
    }

    private void complete(DiagnosticSession s) {
        s.setStatus(Status.COMPLETED);
        s.setCompletedAt(LocalDateTime.now(clock));
        sessions.save(s);
    }

    private void addItems(DiagnosticSession s, int round, List<Long> nodeIds, Context ctx) {
        Set<Long> usedQuestions = items.findBySessionIdOrderByRoundNoAscOrdinalAsc(s.getId()).stream()
                .map(DiagnosticItem::getQuestionId).collect(Collectors.toSet());
        int ordinal = 0;
        for (Long nodeId : nodeIds) {
            Question q = pickQuestion(ctx.questionsByNode.getOrDefault(nodeId, List.of()), usedQuestions);
            if (q == null) {
                continue;
            }
            usedQuestions.add(q.getId());
            DiagnosticItem item = new DiagnosticItem();
            item.setSessionId(s.getId());
            item.setRoundNo(round);
            item.setOrdinal(ordinal++);
            item.setNodeId(nodeId);
            item.setQuestionId(q.getId());
            items.save(item);
        }
    }

    /** 优先没出过的题、难度接近中等的题；题不够时验证轮只能复用原题 */
    static Question pickQuestion(List<Question> candidates, Set<Long> used) {
        Comparator<Question> byMiddle = Comparator.comparingDouble(q -> Math.abs(q.getDifficulty() - 0.5));
        return candidates.stream().filter(q -> !used.contains(q.getId())).min(byMiddle)
                .orElse(candidates.stream().min(byMiddle).orElse(null));
    }

    /** 每个节点取最后一次作答结果（只看到 maxRound 为止） */
    private static Map<Long, Boolean> latestResults(List<DiagnosticItem> all, int maxRound) {
        Map<Long, Boolean> out = new LinkedHashMap<>();
        for (DiagnosticItem i : all) {
            if (i.isAnswered() && i.getRoundNo() <= maxRound) {
                out.put(i.getNodeId(), i.getCorrect());
            }
        }
        return out;
    }

    private SessionView view(DiagnosticSession s) {
        List<DiagnosticItem> round = items.findBySessionIdOrderByRoundNoAscOrdinalAsc(s.getId()).stream()
                .filter(i -> i.getRoundNo() == s.getCurrentRound()).toList();
        ItemView next = s.getStatus() == Status.COMPLETED ? null : round.stream().filter(i -> !i.isAnswered()).findFirst()
                .map(i -> {
                    Question q = questions.findById(i.getQuestionId()).orElseThrow();
                    return new ItemView(i.getId(), i.getRoundNo(), q.getId(), q.getQuestionType(), q.getStem(), q.getOptionsJson());
                }).orElse(null);
        int answered = (int) round.stream().filter(DiagnosticItem::isAnswered).count();
        return new SessionView(s.getId(), s.getGraphId(), s.getCurrentRound(), s.getStatus(), answered, round.size(), next);
    }

    private DiagnosticSession requireOwn(AppUser actor, Long sessionId) {
        DiagnosticSession s = sessions.findById(sessionId).orElseThrow(() -> NotFoundException.of("诊断", sessionId));
        if (!s.getLearnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能做自己的诊断");
        }
        return s;
    }

    private record Context(Map<Long, KnowledgeNode> nodes, Map<Long, List<Long>> prereqs,
                           Map<Long, List<Question>> questionsByNode, Set<Long> testable) {
    }

    private Context context(DiagnosticSession s) {
        Map<Long, KnowledgeNode> ns = nodes.findByGraphIdOrderByOrdinal(s.getGraphId()).stream()
                .filter(n -> !n.isDeleted())
                .collect(Collectors.toMap(KnowledgeNode::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, List<Long>> prereqs = new HashMap<>();
        for (NodeRelation r : relations.findByGraphId(s.getGraphId())) {
            if (r.getRelationType() == RelationType.PREREQUISITE && !r.isDisputed()
                    && ns.containsKey(r.getFromNodeId()) && ns.containsKey(r.getToNodeId())) {
                prereqs.computeIfAbsent(r.getToNodeId(), k -> new ArrayList<>()).add(r.getFromNodeId());
            }
        }
        // 题目挂在书本图谱节点上，按 origin 映射回副本节点
        Map<Long, Long> byOrigin = ns.values().stream().filter(n -> n.getOriginNodeId() != null)
                .collect(Collectors.toMap(KnowledgeNode::getOriginNodeId, KnowledgeNode::getId));
        Map<Long, List<Question>> qs = questions.findByNodeIdIn(byOrigin.keySet()).stream()
                .collect(Collectors.groupingBy(q -> byOrigin.get(q.getNodeId())));
        return new Context(ns, prereqs, qs, qs.keySet());
    }
}
