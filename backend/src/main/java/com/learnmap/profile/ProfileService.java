package com.learnmap.profile;

import com.learnmap.book.BookSection;
import com.learnmap.book.BookSectionRepository;
import com.learnmap.evidence.Evidence;
import com.learnmap.evidence.EvidenceRepository;
import com.learnmap.feynman.FeynmanAttemptRepository;
import com.learnmap.graph.GraphKind;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeGraphRepository;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.plan.PlanTaskRepository;
import com.learnmap.plan.PomodoroSessionRepository;
import com.learnmap.practice.LearnerGraphSnapshot;
import com.learnmap.practice.LearnerGraphSnapshotLoader;
import com.learnmap.practice.PracticeSessionRepository;
import com.learnmap.profile.LearnerProfile.Highlights;
import com.learnmap.profile.ProfileCalculator.Input;
import com.learnmap.profile.ProfileCalculator.NodeInfo;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import com.learnmap.question.QuestionSolution;
import com.learnmap.question.QuestionSolutionRepository;
import com.learnmap.retest.ScheduledRetestRepository;
import com.learnmap.user.AppUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 按需计算画像（数据量小，MVP 不做缓存） */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final KnowledgeGraphRepository graphs;
    private final LearnerGraphSnapshotLoader snapshots;
    private final BookSectionRepository sections;
    private final EvidenceRepository evidences;
    private final QuestionRepository questions;
    private final QuestionSolutionRepository solutions;
    private final FeynmanAttemptRepository feynman;
    private final ScheduledRetestRepository retests;
    private final PlanTaskRepository tasks;
    private final PomodoroSessionRepository pomodoros;
    private final PracticeSessionRepository practice;
    private final UserService users;
    private final Clock clock;

    @Transactional(readOnly = true)
    public LearnerProfile profile(AppUser actor, Long learnerId) {
        users.checkCanView(actor, learnerId);
        return compute(learnerId);
    }

    @Transactional(readOnly = true)
    public Highlights highlights(AppUser actor, Long learnerId) {
        users.checkCanView(actor, learnerId);
        return ProfileCalculator.highlights(compute(learnerId));
    }

    @Transactional(readOnly = true)
    public LearnerProfile compute(Long learnerId) {
        Map<Long, NodeInfo> nodes = new LinkedHashMap<>();
        Map<Long, NodeMastery> mastery = new HashMap<>();
        for (KnowledgeGraph g : graphs.findByOwnerLearnerIdAndKind(learnerId, GraphKind.LEARNER_COPY)) {
            LearnerGraphSnapshot snap = snapshots.load(g);
            Map<Long, String> titles = sections.findByBookIdOrderByOrdinal(g.getBookId()).stream()
                    .collect(Collectors.toMap(BookSection::getId, BookSection::getTitle));
            snap.nodes().forEach((id, n) -> {
                Long ch = snap.chapterOf().get(id);
                nodes.put(id, new NodeInfo(id, g.getId(), ch, titles.get(ch), n.getName(), n.getNodeType()));
            });
            mastery.putAll(snap.mastery());
        }
        List<Evidence> evs = evidences.findByLearnerIdOrderByIdAsc(learnerId);
        Set<Long> qIds = evs.stream().map(Evidence::getQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Question> qs = questions.findAllById(qIds).stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        Set<Long> sIds = evs.stream().map(Evidence::getSolutionId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> solutionNames = solutions.findAllById(sIds).stream()
                .collect(Collectors.toMap(QuestionSolution::getId, QuestionSolution::getName));
        return ProfileCalculator.compute(new Input(learnerId, LocalDate.now(clock), nodes, mastery, evs, qs, solutionNames,
                feynman.findByLearnerIdOrderByIdDesc(learnerId), retests.findByLearnerIdAndDoneTrue(learnerId),
                tasks.findByLearnerId(learnerId), pomodoros.findByLearnerIdOrderByStartedAtDesc(learnerId),
                practice.findByLearnerIdOrderByIdDesc(learnerId)));
    }
}
