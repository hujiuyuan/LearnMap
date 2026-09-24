package com.learnmap.practice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.AnswerService;
import com.learnmap.evidence.AnswerService.AnswerRequest;
import com.learnmap.evidence.AnswerService.AnswerResult;
import com.learnmap.evidence.ErrorType;
import com.learnmap.evidence.SelfConfidence;
import com.learnmap.flag.FlagKind;
import com.learnmap.flag.FlagService;
import com.learnmap.graph.GraphKind;
import com.learnmap.graph.GraphService;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.practice.PracticeFlow.Decision;
import com.learnmap.practice.PracticeSession.Status;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import com.learnmap.question.QuestionType;
import com.learnmap.retest.RetestReason;
import com.learnmap.retest.RetestService;
import com.learnmap.user.AppUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 三种练习型学习方式（刻意练习、交错练习、第一性原理）的通用执行：建会话、按流程出一批题、判分写证据、推进到下一批。
 */
@Service
public class PracticeService {

    private final PracticeSessionRepository sessions;
    private final PracticeItemRepository items;
    private final GraphService graphs;
    private final LearnerGraphSnapshotLoader snapshots;
    private final QuestionRepository questions;
    private final AnswerService answers;
    private final RetestService retests;
    private final FlagService flags;
    private final UserService users;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<PracticeMode, PracticeFlow<?>> flows = new EnumMap<>(PracticeMode.class);

    public PracticeService(PracticeSessionRepository sessions, PracticeItemRepository items, GraphService graphs,
                           LearnerGraphSnapshotLoader snapshots, QuestionRepository questions, AnswerService answers,
                           RetestService retests, FlagService flags, UserService users, ObjectMapper json, Clock clock,
                           List<PracticeFlow<?>> flowList) {
        this.sessions = sessions;
        this.items = items;
        this.graphs = graphs;
        this.snapshots = snapshots;
        this.questions = questions;
        this.answers = answers;
        this.retests = retests;
        this.flags = flags;
        this.users = users;
        this.json = json;
        this.clock = clock;
        flowList.forEach(f -> flows.put(f.mode(), f));
    }

    // ---------- 视图 ----------

    public record ItemView(Long id, Long questionId, QuestionType type, String stem, String optionsJson) {
    }

    /** 第一性原理补缺时先看的讲解：定义 + 原文出处由前端按节点再取 */
    public record Lesson(Long nodeId, String name, String definition) {
    }

    public record SessionView(Long id, PracticeMode mode, Long targetNodeId, PracticePhase phase, Status status,
                              int answeredInBatch, int batchSize, ItemView next, Lesson lesson, String summary) {
    }

    public record AnswerOutcome(boolean correct, String expectedAnswer, SessionView session) {
    }

    public record SessionSummary(Long id, Long graphId, PracticeMode mode, Long targetNodeId, Status status,
                                 String summary, LocalDateTime createdAt) {
    }

    // ---------- 流程 ----------

    @Transactional
    public SessionView start(AppUser actor, Long graphId, PracticeMode mode, Long targetNodeId) {
        KnowledgeGraph g = graphs.require(graphId);
        if (g.getKind() != GraphKind.LEARNER_COPY || !actor.getId().equals(g.getOwnerLearnerId())) {
            throw new ForbiddenException("只能在自己的图谱上练习");
        }
        LearnerGraphSnapshot snap = snapshots.load(g);
        PracticeSession s = new PracticeSession();
        s.setLearnerId(actor.getId());
        s.setGraphId(graphId);
        s.setMode(mode);
        s.setTargetNodeId(targetNodeId);
        s.setPhase(PracticePhase.DONE);
        s.setStatus(Status.IN_PROGRESS);
        s = sessions.save(s);
        apply(s, snap, flow(mode).begin(snap, targetNodeId, s.getId()));
        return view(s);
    }

    public SessionView get(AppUser actor, Long sessionId) {
        return view(requireOwn(actor, sessionId));
    }

    public List<SessionSummary> history(AppUser actor, Long learnerId) {
        users.checkCanView(actor, learnerId);
        return sessions.findByLearnerIdOrderByIdDesc(learnerId).stream()
                .map(s -> new SessionSummary(s.getId(), s.getGraphId(), s.getMode(), s.getTargetNodeId(), s.getStatus(),
                        s.getSummary(), s.getCreatedAt()))
                .toList();
    }

    @Transactional
    public AnswerOutcome answer(AppUser actor, Long sessionId, Long itemId, String answer, ErrorType errorType,
                                SelfConfidence selfConfidence, Integer durationSec) {
        PracticeSession s = requireOwn(actor, sessionId);
        if (s.getStatus() != Status.IN_PROGRESS) {
            throw new IllegalStateException("这次练习已经结束");
        }
        PracticeItem item = items.findById(itemId).filter(i -> i.getSessionId().equals(s.getId()))
                .orElseThrow(() -> NotFoundException.of("练习题", itemId));
        if (item.isAnswered() || item.getStep() != s.getStep()) {
            throw new IllegalStateException("这道题已经答过了");
        }
        AnswerResult r = answers.record(actor, new AnswerRequest(item.getQuestionId(), activityType(s.getMode(), item.getPhase()),
                answer, null, null, durationSec, selfConfidence, errorType));
        item.setAnswered(true);
        item.setCorrect(r.correct());
        item.setErrorType(r.correct() ? null : errorType);
        item.setAnsweredAt(LocalDateTime.now(clock));
        items.save(item);

        List<PracticeItem> all = items.findBySessionIdOrderByIdAsc(s.getId());
        List<PracticeItem> batch = all.stream().filter(i -> i.getStep() == s.getStep()).toList();
        if (batch.stream().allMatch(PracticeItem::isAnswered)) {
            KnowledgeGraph g = graphs.require(s.getGraphId());
            LearnerGraphSnapshot snap = snapshots.load(g);
            apply(s, snap, advance(flow(s.getMode()), snap, s, batch, all));
        }
        return new AnswerOutcome(r.correct(), r.expectedAnswer(), view(s));
    }

    private <S> Decision<S> advance(PracticeFlow<S> flow, LearnerGraphSnapshot snap, PracticeSession s,
                                    List<PracticeItem> batch, List<PracticeItem> all) {
        return flow.next(snap, s.getTargetNodeId(), readState(s, flow.stateType()), s.getPhase(), batch, all);
    }

    private void apply(PracticeSession s, LearnerGraphSnapshot snap, Decision<?> d) {
        ActivityType source = s.getMode() == PracticeMode.INTERLEAVED ? ActivityType.INTERLEAVED : ActivityType.PRACTICE;
        d.learned().forEach(n -> retests.scheduleRetention(s.getLearnerId(), n, source));
        d.recheck().forEach(n -> retests.schedule(s.getLearnerId(), n, RetestReason.DELIBERATE_RECHECK, source));
        s.setStateJson(writeState(d.state()));
        if (d.done()) {
            if (d.state() instanceof FirstPrinciplesFlow.State fp) {
                fp.flagged.forEach(n -> flags.systemFlag(s.getLearnerId(), n, FlagKind.STUCK, "第一性原理补缺两轮都没通过"));
            }
            s.setPhase(PracticePhase.DONE);
            s.setStatus(Status.COMPLETED);
            s.setSummary(d.summary());
            s.setCompletedAt(LocalDateTime.now(clock));
            sessions.save(s);
            return;
        }
        s.setPhase(d.phase());
        s.setStep(s.getStep() + 1);
        sessions.save(s);
        if (addItems(s, snap, d.phase(), d.nodes()) == 0) {
            s.setPhase(PracticePhase.DONE);
            s.setStatus(Status.COMPLETED);
            s.setSummary("这些知识点还没有题目，暂时没法练习。");
            s.setCompletedAt(LocalDateTime.now(clock));
            sessions.save(s);
        }
    }

    /** 每道题尽量不重复：同一个节点按“没出过的 → 难度接近中等”轮流取题 */
    private int addItems(PracticeSession s, LearnerGraphSnapshot snap, PracticePhase phase, List<Long> nodeIds) {
        Map<Long, Long> used = items.findBySessionIdOrderByIdAsc(s.getId()).stream()
                .collect(Collectors.groupingBy(PracticeItem::getQuestionId, Collectors.counting()));
        Map<Long, Long> usedNow = new HashMap<>(used);
        int ordinal = 0;
        for (Long nodeId : nodeIds) {
            List<Question> candidates = snap.questionsByNode().getOrDefault(nodeId, List.of());
            if (candidates.isEmpty()) {
                continue;
            }
            Question q = candidates.stream()
                    .min(Comparator.comparingLong((Question c) -> usedNow.getOrDefault(c.getId(), 0L))
                            .thenComparingDouble(c -> Math.abs(c.getDifficulty() - 0.5))
                            .thenComparing(Question::getId))
                    .orElseThrow();
            usedNow.merge(q.getId(), 1L, Long::sum);
            PracticeItem item = new PracticeItem();
            item.setSessionId(s.getId());
            item.setPhase(phase);
            item.setStep(s.getStep());
            item.setOrdinal(ordinal++);
            item.setNodeId(nodeId);
            item.setQuestionId(q.getId());
            items.save(item);
        }
        return ordinal;
    }

    private SessionView view(PracticeSession s) {
        List<PracticeItem> batch = items.findBySessionIdOrderByIdAsc(s.getId()).stream()
                .filter(i -> i.getStep() == s.getStep()).toList();
        ItemView next = s.getStatus() == Status.COMPLETED ? null : batch.stream().filter(i -> !i.isAnswered()).findFirst()
                .map(i -> {
                    Question q = questions.findById(i.getQuestionId()).orElseThrow();
                    return new ItemView(i.getId(), q.getId(), q.getQuestionType(), q.getStem(), q.getOptionsJson());
                }).orElse(null);
        Lesson lesson = null;
        if (s.getStatus() == Status.IN_PROGRESS) {
            Long lessonNode = lessonNode(flow(s.getMode()), s);
            if (lessonNode != null) {
                KnowledgeGraph g = graphs.require(s.getGraphId());
                KnowledgeNode n = snapshots.load(g).nodes().get(lessonNode);
                lesson = new Lesson(n.getId(), n.getName(), n.getDefinition());
            }
        }
        int answered = (int) batch.stream().filter(PracticeItem::isAnswered).count();
        return new SessionView(s.getId(), s.getMode(), s.getTargetNodeId(), s.getPhase(), s.getStatus(), answered,
                batch.size(), next, lesson, s.getSummary());
    }

    private <S> Long lessonNode(PracticeFlow<S> flow, PracticeSession s) {
        return flow.lessonNode(readState(s, flow.stateType()), s.getPhase());
    }

    static ActivityType activityType(PracticeMode mode, PracticePhase phase) {
        return switch (phase) {
            case MIXED -> ActivityType.INTERLEAVED;
            case VERIFY, PROBE, GOAL -> ActivityType.QUIZ;
            default -> ActivityType.PRACTICE;
        };
    }

    private PracticeFlow<?> flow(PracticeMode mode) {
        PracticeFlow<?> f = flows.get(mode);
        if (f == null) {
            throw new IllegalArgumentException("不支持的学习方式：" + mode);
        }
        return f;
    }

    private PracticeSession requireOwn(AppUser actor, Long sessionId) {
        PracticeSession s = sessions.findById(sessionId).orElseThrow(() -> NotFoundException.of("练习", sessionId));
        if (!s.getLearnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能做自己的练习");
        }
        return s;
    }

    private <S> S readState(PracticeSession s, Class<S> type) {
        try {
            return s.getStateJson() == null ? type.getDeclaredConstructor().newInstance() : json.readValue(s.getStateJson(), type);
        } catch (ReflectiveOperationException | JsonProcessingException e) {
            throw new IllegalStateException("练习状态损坏：" + s.getId(), e);
        }
    }

    private String writeState(Object state) {
        try {
            return json.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
