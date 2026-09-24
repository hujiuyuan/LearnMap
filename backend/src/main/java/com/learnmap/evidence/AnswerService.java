package com.learnmap.evidence;

import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.graph.GraphService;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.mastery.MasteryService;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import com.learnmap.question.QuestionSolution;
import com.learnmap.question.QuestionSolutionRepository;
import com.learnmap.question.SolutionStep;
import com.learnmap.question.SolutionStepRepository;
import com.learnmap.user.AppUser;
import com.learnmap.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记录一次作答并写成证据（产品方案 5.2 一题多解）：
 * <ul>
 *   <li>做对：只给实际用的那条解法上的知识点加分，没用到的解法不加不扣；</li>
 *   <li>分步作答做错：找到出错的那一步，只扣那一步的知识点，前面做对的步骤照常加分，后面的步骤没有证据；</li>
 *   <li>没说用哪种解法：证据只记在题目的主考查节点上。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AnswerService {

    private final QuestionRepository questions;
    private final QuestionSolutionRepository solutions;
    private final SolutionStepRepository steps;
    private final EvidenceRepository evidences;
    private final GraphService graphs;
    private final MasteryService mastery;

    /**
     * @param answer      最终答案；分步作答时可以不填
     * @param solutionId  学习者用的解法（答后选解法 / 指定解法题）
     * @param stepAnswers 分步填空：步骤 id → 该步答案
     */
    public record AnswerRequest(Long questionId, ActivityType activityType, String answer, Long solutionId,
                                Map<Long, String> stepAnswers, Integer durationSec,
                                SelfConfidence selfConfidence, ErrorType errorType) {
    }

    public record NodeUpdate(Long nodeId, String nodeName, EvidenceResult result, double mastery, double confidence) {
    }

    public record AnswerResult(boolean correct, String expectedAnswer, List<NodeUpdate> updates) {
    }

    @Transactional
    public AnswerResult record(AppUser actor, AnswerRequest req) {
        if (actor.getRole() != Role.LEARNER) {
            throw new ForbiddenException("只有学习者可以作答");
        }
        Question q = questions.findById(req.questionId()).orElseThrow(() -> NotFoundException.of("题目", req.questionId()));
        Boolean overall = req.answer() == null ? null : AnswerGrader.matches(q.getAnswer(), req.answer());

        // 主节点 id（MASTER 图谱）→ 结果，按出现顺序
        Map<Long, EvidenceResult> outcome = new LinkedHashMap<>();
        if (req.solutionId() != null) {
            QuestionSolution solution = solutions.findById(req.solutionId())
                    .filter(s -> s.getQuestionId().equals(q.getId()))
                    .orElseThrow(() -> new IllegalArgumentException("这道题没有这个解法：" + req.solutionId()));
            List<SolutionStep> path = steps.findBySolutionIdOrderByOrdinal(solution.getId());
            if (req.stepAnswers() != null && !req.stepAnswers().isEmpty()) {
                boolean allCorrect = true;
                for (SolutionStep step : path) {
                    String given = req.stepAnswers().get(step.getId());
                    if (given == null || step.getAnswer() == null) {
                        continue;
                    }
                    if (AnswerGrader.matches(step.getAnswer(), given)) {
                        outcome.putIfAbsent(step.getNodeId(), EvidenceResult.CORRECT);
                    } else {
                        outcome.put(step.getNodeId(), EvidenceResult.INCORRECT);
                        allCorrect = false;
                        break;
                    }
                }
                if (overall == null) {
                    overall = allCorrect;
                }
            } else if (overall == null) {
                throw new IllegalArgumentException("请提交答案或分步答案");
            } else if (overall) {
                path.forEach(step -> outcome.putIfAbsent(step.getNodeId(), EvidenceResult.CORRECT));
            } else {
                outcome.put(q.getNodeId(), EvidenceResult.INCORRECT);
            }
        } else {
            if (overall == null) {
                throw new IllegalArgumentException("请提交答案");
            }
            outcome.put(q.getNodeId(), overall ? EvidenceResult.CORRECT : EvidenceResult.INCORRECT);
        }

        Map<Long, KnowledgeNode> learnerNodes = graphs.learnerNodesFor(actor.getId(), q.getBookId(), outcome.keySet());
        List<NodeUpdate> updates = new ArrayList<>();
        for (Map.Entry<Long, EvidenceResult> e : outcome.entrySet()) {
            KnowledgeNode node = learnerNodes.get(e.getKey());
            if (node == null) {
                continue; // 学习者删掉了这个节点，或副本来自没有该节点的旧版本
            }
            Evidence ev = new Evidence();
            ev.setLearnerId(actor.getId());
            ev.setNodeId(node.getId());
            ev.setActivityType(req.activityType() == null ? ActivityType.PRACTICE : req.activityType());
            ev.setQuestionId(q.getId());
            ev.setSolutionId(req.solutionId());
            ev.setResult(e.getValue());
            ev.setScore(e.getValue() == EvidenceResult.CORRECT ? 1.0 : 0.0);
            ev.setDifficulty(q.getDifficulty());
            ev.setDurationSec(req.durationSec());
            ev.setSelfConfidence(req.selfConfidence());
            ev.setErrorType(e.getValue() == EvidenceResult.INCORRECT ? req.errorType() : null);
            evidences.save(ev);
            NodeMastery m = mastery.apply(ev, node);
            updates.add(new NodeUpdate(node.getId(), node.getName(), e.getValue(), m.getMastery(), m.getConfidence()));
        }
        return new AnswerResult(overall, q.getAnswer(), updates);
    }
}
