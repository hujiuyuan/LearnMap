package com.learnmap.retest;

import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.AnswerService;
import com.learnmap.evidence.AnswerService.AnswerRequest;
import com.learnmap.evidence.AnswerService.AnswerResult;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import com.learnmap.question.QuestionType;
import com.learnmap.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 延时复测：学完 3 / 7 / 21 天各测一次“还记不记得”，这是评估学习方式效果的关键数据。
 * 复测题优先选没做过的、难度中等的题。
 */
@Service
@RequiredArgsConstructor
public class RetestService {

    private final ScheduledRetestRepository retests;
    private final KnowledgeNodeRepository nodes;
    private final QuestionRepository questions;
    private final AnswerService answers;
    private final Clock clock;

    public record RetestView(Long id, Long nodeId, String nodeName, RetestReason reason, LocalDateTime dueAt,
                             Long questionId, QuestionType type, String stem, String optionsJson) {
    }

    /** 某个节点学会了：安排 3 / 7 / 21 天的复测（已安排过的不重复） */
    @Transactional
    public void scheduleRetention(Long learnerId, Long nodeId, ActivityType sourceMode) {
        for (RetestReason r : List.of(RetestReason.RETENTION_3D, RetestReason.RETENTION_7D, RetestReason.RETENTION_21D)) {
            schedule(learnerId, nodeId, r, sourceMode);
        }
    }

    @Transactional
    public void schedule(Long learnerId, Long nodeId, RetestReason reason, ActivityType sourceMode) {
        if (retests.existsByLearnerIdAndNodeIdAndReasonAndDoneFalse(learnerId, nodeId, reason)) {
            return;
        }
        ScheduledRetest r = new ScheduledRetest();
        r.setLearnerId(learnerId);
        r.setNodeId(nodeId);
        r.setReason(reason);
        r.setSourceMode(sourceMode == null ? null : sourceMode.name());
        r.setDueAt(LocalDateTime.now(clock).plusDays(reason.days));
        retests.save(r);
    }

    public List<RetestView> due(AppUser learner) {
        return retests.findByLearnerIdAndDoneFalseAndDueAtLessThanEqualOrderByDueAtAsc(learner.getId(), LocalDateTime.now(clock))
                .stream().map(this::view).flatMap(Optional::stream).toList();
    }

    @Transactional
    public AnswerResult answer(AppUser actor, Long retestId, String answer) {
        ScheduledRetest r = retests.findById(retestId).orElseThrow(() -> NotFoundException.of("复测", retestId));
        if (!r.getLearnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能做自己的复测");
        }
        if (r.isDone()) {
            throw new IllegalStateException("这次复测已经做过了");
        }
        Question q = pick(r).orElseThrow(() -> new IllegalStateException("这个知识点没有题目"));
        AnswerResult result = answers.record(actor, new AnswerRequest(q.getId(), ActivityType.RETEST, answer,
                null, null, null, null, null));
        r.setDone(true);
        r.setCorrect(result.correct());
        r.setDoneAt(LocalDateTime.now(clock));
        return result;
    }

    private Optional<RetestView> view(ScheduledRetest r) {
        KnowledgeNode n = nodes.findById(r.getNodeId()).orElse(null);
        if (n == null || n.isDeleted()) {
            return Optional.empty();
        }
        return pick(r).map(q -> new RetestView(r.getId(), n.getId(), n.getName(), r.getReason(), r.getDueAt(),
                q.getId(), q.getQuestionType(), q.getStem(), q.getOptionsJson()));
    }

    /** 同一个节点的几次复测轮流用不同的题（按复测 id 取模），难度中等优先 */
    private Optional<Question> pick(ScheduledRetest r) {
        KnowledgeNode n = nodes.findById(r.getNodeId()).orElse(null);
        if (n == null) {
            return Optional.empty();
        }
        Long master = n.getOriginNodeId() != null ? n.getOriginNodeId() : n.getId();
        List<Question> qs = questions.findByNodeIdIn(List.of(master)).stream()
                .sorted(Comparator.comparingDouble((Question q) -> Math.abs(q.getDifficulty() - 0.5)).thenComparing(Question::getId))
                .toList();
        return qs.isEmpty() ? Optional.empty() : Optional.of(qs.get((int) (r.getId() % qs.size())));
    }
}
