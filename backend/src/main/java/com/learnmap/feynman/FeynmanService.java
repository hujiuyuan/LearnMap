package com.learnmap.feynman;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.ai.AiProvider;
import com.learnmap.ai.AiProvider.FeynmanGrade;
import com.learnmap.ai.AiProvider.NodeBrief;
import com.learnmap.book.SourceChunk;
import com.learnmap.book.SourceChunkRepository;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.Evidence;
import com.learnmap.evidence.EvidenceRepository;
import com.learnmap.evidence.EvidenceResult;
import com.learnmap.graph.GraphService;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.graph.NodeSourceRepository;
import com.learnmap.graph.GraphKind;
import com.learnmap.mastery.MasteryService;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.retest.RetestService;
import com.learnmap.user.AppUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 费曼讲解：学习者把一个概念“讲给小学生听”（打字，或拍手写讲解的照片走 OCR），
 * AI 按节点要点逐条对照，指出讲漏、讲错的地方，再追问 1~2 个问题。评分写成 FEYNMAN 证据。
 */
@Service
@RequiredArgsConstructor
public class FeynmanService {

    /** 达到这个分数视为讲清楚了，安排延时复测 */
    static final double PASS_SCORE = 0.8;
    /** 低于这个分数，或 AI 找出错误陈述时，交给辅导者复核 */
    static final double REVIEW_BELOW = 0.4;
    /** 费曼讲解按“中等偏难”计入掌握度 */
    static final double FEYNMAN_DIFFICULTY = 0.6;

    private final AiProvider ai;
    private final FeynmanAttemptRepository attempts;
    private final KnowledgeNodeRepository nodes;
    private final NodeSourceRepository nodeSources;
    private final SourceChunkRepository chunks;
    private final GraphService graphs;
    private final EvidenceRepository evidences;
    private final MasteryService mastery;
    private final RetestService retests;
    private final UserService users;
    private final ObjectMapper json;

    public record AttemptView(Long id, Long nodeId, Long parentAttemptId, String explanation, boolean fromImage,
                              double score, List<String> covered, List<String> missing, List<String> incorrect,
                              List<String> followUps, boolean needsReview, Double mastery,
                              Long learnerId, String nodeName, Double reviewedScore, String reviewNote,
                              LocalDateTime createdAt) {
    }

    /**
     * @param imageBase64 手写讲解的照片（和 explanation 二选一）
     * @param parentAttemptId 回答上一次的追问时填
     */
    @Transactional
    public AttemptView explain(AppUser actor, Long graphId, Long nodeId, String explanation, String imageBase64,
                               String imageMimeType, Long parentAttemptId) {
        KnowledgeGraph g = graphs.require(graphId);
        if (g.getKind() != GraphKind.LEARNER_COPY || !actor.getId().equals(g.getOwnerLearnerId())) {
            throw new ForbiddenException("只能在自己的图谱上做费曼讲解");
        }
        KnowledgeNode node = nodes.findById(nodeId).filter(n -> n.getGraphId().equals(graphId) && !n.isDeleted())
                .orElseThrow(() -> NotFoundException.of("节点", nodeId));
        boolean fromImage = explanation == null || explanation.isBlank();
        String text = explanation;
        if (fromImage) {
            if (imageBase64 == null || imageBase64.isBlank()) {
                throw new IllegalArgumentException("请写下你的讲解，或者上传一张手写讲解的照片");
            }
            text = ai.ocr(Base64.getDecoder().decode(imageBase64), imageMimeType == null ? "image/jpeg" : imageMimeType);
        }
        FeynmanAttempt parent = null;
        if (parentAttemptId != null) {
            parent = attempts.findById(parentAttemptId).filter(a -> a.getLearnerId().equals(actor.getId()) && a.getNodeId().equals(nodeId))
                    .orElseThrow(() -> NotFoundException.of("讲解", parentAttemptId));
        }

        // 追问的回答连同上一次讲解一起评，这样补上的要点能算进去
        String graded = parent == null ? text : parent.getExplanation() + "\n\n【追问的回答】\n" + text;
        FeynmanGrade grade = ai.gradeFeynman(brief(node), graded);

        FeynmanAttempt a = new FeynmanAttempt();
        a.setLearnerId(actor.getId());
        a.setNodeId(nodeId);
        a.setParentAttemptId(parentAttemptId);
        a.setExplanation(text);
        a.setFromImage(fromImage);
        a.setScore(grade.score());
        a.setCoveredJson(write(grade.coveredPoints()));
        a.setMissingJson(write(grade.missingPoints()));
        a.setIncorrectJson(write(grade.incorrectStatements()));
        a.setFollowUpsJson(write(parent == null ? grade.followUpQuestions() : List.of()));
        a.setGrader(ai.name());
        a.setNeedsReview(grade.score() < REVIEW_BELOW || !grade.incorrectStatements().isEmpty());
        a = attempts.save(a);

        Evidence ev = new Evidence();
        ev.setLearnerId(actor.getId());
        ev.setNodeId(nodeId);
        ev.setActivityType(ActivityType.FEYNMAN);
        ev.setResult(grade.score() >= PASS_SCORE ? EvidenceResult.CORRECT
                : grade.score() <= 0.2 ? EvidenceResult.INCORRECT : EvidenceResult.PARTIAL);
        ev.setScore(grade.score());
        ev.setDifficulty(FEYNMAN_DIFFICULTY);
        evidences.save(ev);
        NodeMastery m = mastery.apply(ev, node);
        if (grade.score() >= PASS_SCORE) {
            retests.scheduleRetention(actor.getId(), nodeId, ActivityType.FEYNMAN);
        }
        return view(a, m.getMastery());
    }

    /**
     * 辅导者复核 AI 评分：复核分另记一条 FEYNMAN 证据（人的判断也是证据），并清掉“待复核”。
     */
    @Transactional
    public AttemptView review(AppUser actor, Long attemptId, double score, String note) {
        FeynmanAttempt a = attempts.findById(attemptId).orElseThrow(() -> NotFoundException.of("讲解", attemptId));
        if (!users.isTutorOf(actor.getId(), a.getLearnerId())) {
            throw new ForbiddenException("只有学习者的辅导者可以复核讲解");
        }
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("复核分要在 0 到 1 之间");
        }
        a.setReviewedBy(actor.getId());
        a.setReviewedScore(score);
        a.setReviewNote(note);
        a.setReviewedAt(LocalDateTime.now());
        a.setNeedsReview(false);
        KnowledgeNode node = nodes.findById(a.getNodeId()).orElseThrow();
        Evidence ev = new Evidence();
        ev.setLearnerId(a.getLearnerId());
        ev.setNodeId(a.getNodeId());
        ev.setActivityType(ActivityType.FEYNMAN);
        ev.setResult(score >= PASS_SCORE ? EvidenceResult.CORRECT : score <= 0.2 ? EvidenceResult.INCORRECT : EvidenceResult.PARTIAL);
        ev.setScore(score);
        ev.setDifficulty(FEYNMAN_DIFFICULTY);
        evidences.save(ev);
        NodeMastery m = mastery.apply(ev, node);
        if (score >= PASS_SCORE) {
            retests.scheduleRetention(a.getLearnerId(), a.getNodeId(), ActivityType.FEYNMAN);
        }
        return view(a, m.getMastery());
    }

    /** 辅导者名下所有学习者待复核的讲解 */
    public List<AttemptView> pendingReviews(AppUser tutor) {
        List<Long> learners = users.learnersOf(tutor.getId()).stream().map(AppUser::getId).toList();
        return learners.isEmpty() ? List.of() : attempts.findByLearnerIdInAndNeedsReviewTrueOrderByIdDesc(learners)
                .stream().map(a -> view(a, null)).toList();
    }

    public List<AttemptView> history(AppUser actor, Long learnerId, Long nodeId) {
        users.checkCanView(actor, learnerId);
        return (nodeId == null ? attempts.findByLearnerIdOrderByIdDesc(learnerId)
                : attempts.findByLearnerIdAndNodeIdOrderByIdDesc(learnerId, nodeId))
                .stream().map(a -> view(a, null)).toList();
    }

    private NodeBrief brief(KnowledgeNode node) {
        Long sourceNode = node.getOriginNodeId() != null ? node.getOriginNodeId() : node.getId();
        List<String> excerpts = nodeSources.findByNodeIdIn(List.of(sourceNode)).stream()
                .map(ns -> chunks.findById(ns.getChunkId()).map(SourceChunk::getContent).orElse(null))
                .filter(c -> c != null).limit(3).toList();
        return new NodeBrief(node.getName(), node.getDefinition(), node.getNodeType(), excerpts);
    }

    private AttemptView view(FeynmanAttempt a, Double mastery) {
        return new AttemptView(a.getId(), a.getNodeId(), a.getParentAttemptId(), a.getExplanation(), a.isFromImage(),
                a.getScore(), read(a.getCoveredJson()), read(a.getMissingJson()), read(a.getIncorrectJson()),
                read(a.getFollowUpsJson()), a.isNeedsReview(), mastery, a.getLearnerId(),
                nodes.findById(a.getNodeId()).map(KnowledgeNode::getName).orElse(null), a.getReviewedScore(),
                a.getReviewNote(), a.getCreatedAt());
    }

    private String write(List<String> list) {
        try {
            return json.writeValueAsString(list == null ? List.of() : list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> read(String s) {
        try {
            return s == null ? List.of() : json.readValue(s, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
