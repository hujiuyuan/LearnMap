package com.learnmap.profile;

import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.ErrorType;
import com.learnmap.evidence.Evidence;
import com.learnmap.evidence.EvidenceResult;
import com.learnmap.evidence.SelfConfidence;
import com.learnmap.feynman.FeynmanAttempt;
import com.learnmap.graph.NodeType;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.plan.PlanTask;
import com.learnmap.plan.PomodoroSession;
import com.learnmap.plan.TaskStatus;
import com.learnmap.plan.TaskType;
import com.learnmap.profile.LearnerProfile.ModeStat;
import com.learnmap.profile.ProfileCalculator.Input;
import com.learnmap.profile.ProfileCalculator.NodeInfo;
import com.learnmap.question.Question;
import com.learnmap.retest.RetestReason;
import com.learnmap.retest.ScheduledRetest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileCalculatorTest {

    static final LocalDate TODAY = LocalDate.of(2026, 9, 24);

    final Map<Long, NodeInfo> nodes = new LinkedHashMap<>();
    final Map<Long, NodeMastery> mastery = new LinkedHashMap<>();
    final List<Evidence> evidences = new ArrayList<>();
    final Map<Long, Question> questions = new LinkedHashMap<>();
    final List<FeynmanAttempt> feynman = new ArrayList<>();
    final List<ScheduledRetest> retests = new ArrayList<>();
    final List<PlanTask> tasks = new ArrayList<>();
    final List<PomodoroSession> pomodoros = new ArrayList<>();
    long ids = 1;

    ProfileCalculatorTest() {
        nodes.put(1L, new NodeInfo(1L, 9L, 100L, "第一章", "等式的性质", NodeType.CONCEPT));
        nodes.put(2L, new NodeInfo(2L, 9L, 100L, "第一章", "移项", NodeType.METHOD));
        nodes.put(3L, new NodeInfo(3L, 9L, 200L, "第二章", "应用题", NodeType.SYNTHESIS));
        mastery.put(1L, m(1L, 0.9, 0.8));
        mastery.put(2L, m(2L, 0.3, 0.5));
        Question q = new Question();
        ReflectionTestUtils.setField(q, "id", 50L);
        q.setAbilities("CALCULATION,READING");
        questions.put(50L, q);
    }

    LearnerProfile compute() {
        return ProfileCalculator.compute(new Input(7L, TODAY, nodes, mastery, evidences, questions, Map.of(),
                feynman, retests, tasks, pomodoros, List.of()));
    }

    @Test
    void knowledgeCountsStatusesPerChapter() {
        var k = compute().knowledge();
        assertThat(k.total()).isEqualTo(3);
        assertThat(k.mastered()).isEqualTo(1);
        assertThat(k.weak()).isEqualTo(1);
        assertThat(k.untouched()).isEqualTo(1);
        assertThat(k.chapters()).extracting(LearnerProfile.ChapterStat::title).containsExactly("第一章", "第二章");
        assertThat(k.chapters().get(0).mastered()).isEqualTo(1);
        assertThat(k.chapters().get(0).avgMastery()).isEqualTo(0.6);
    }

    @Test
    void abilitiesErrorsAndCalibration() {
        ev(2L, ActivityType.PRACTICE, true, 50L, SelfConfidence.SURE, null, TODAY);
        ev(2L, ActivityType.PRACTICE, false, 50L, SelfConfidence.SURE, ErrorType.CALCULATION, TODAY);
        ev(2L, ActivityType.PRACTICE, false, 50L, null, ErrorType.CALCULATION, TODAY);
        ev(2L, ActivityType.PRACTICE, false, 50L, null, null, TODAY);
        ev(1L, ActivityType.QUIZ, true, 51L, SelfConfidence.DONT_KNOW, null, TODAY);
        FeynmanAttempt a = new FeynmanAttempt();
        a.setScore(0.3);
        a.setReviewedScore(0.7);
        feynman.add(a);

        LearnerProfile p = compute();
        var calc = p.abilities().abilities().stream().filter(s -> s.label().equals("计算")).findFirst().orElseThrow();
        assertThat(calc.attempts()).isEqualTo(4);
        assertThat(calc.accuracy()).isEqualTo(0.25);
        assertThat(p.abilities().expression()).isEqualTo(0.7);
        // 三条有自评：SURE 对、SURE 错（过度自信）、不会却对（低估自己）
        assertThat(p.abilities().calibration().total()).isEqualTo(3);
        assertThat(p.abilities().calibration().overconfident()).isEqualTo(1);
        assertThat(p.abilities().calibration().underconfident()).isEqualTo(1);

        assertThat(p.errors().wrongTotal()).isEqualTo(3);
        assertThat(p.errors().unlabeled()).isEqualTo(1);
        assertThat(p.errors().byType().get(0).label()).isEqualTo("计算");
        assertThat(p.errors().byType().get(0).share()).isEqualTo(1.0);
    }

    @Test
    void modeProfileUsesRetentionFirstAndSuggestsTheBestMode() {
        for (int i = 0; i < 3; i++) {
            ev(1L, ActivityType.FEYNMAN, true, null, null, null, TODAY);
            retest(1L, "FEYNMAN", true);
            retest(1L, "CARD", i == 0);
        }
        retest(1L, "CARD", false);
        LearnerProfile p = compute();
        ModeStat feynmanOnConcept = p.modes().stats().stream()
                .filter(s -> s.mode().equals("FEYNMAN") && s.nodeType() == NodeType.CONCEPT).findFirst().orElseThrow();
        assertThat(feynmanOnConcept.evidences()).isEqualTo(3);
        assertThat(feynmanOnConcept.retentionRate()).isEqualTo(1.0);
        assertThat(feynmanOnConcept.gainPer10Min()).isPositive();
        assertThat(p.modes().suggestions()).containsExactly("概念类知识点你用「费曼讲解」效果最好：学会后复测还记得 100%（3 次复测）");
        assertThat(ProfileCalculator.highlights(p).bestMode()).contains("费曼讲解");
    }

    @Test
    void behaviorStreakPlanCompletionAndPomodoros() {
        ev(1L, ActivityType.CARD, true, null, null, null, TODAY);
        ev(1L, ActivityType.CARD, true, null, null, null, TODAY.minusDays(1));
        ev(1L, ActivityType.CARD, true, null, null, null, TODAY.minusDays(3));
        task(TODAY.minusDays(2), TaskStatus.DONE, TODAY.minusDays(1));
        task(TODAY.minusDays(2), TaskStatus.TODO, null);
        task(TODAY.minusDays(1), TaskStatus.SKIPPED, null);
        task(TODAY, TaskStatus.TODO, null);  // 今天还没做的不算
        pomodoro(true, 0);
        pomodoro(false, 2);

        var b = compute().behavior();
        assertThat(b.streak()).isEqualTo(2);
        assertThat(b.activeDays14()).isEqualTo(3);
        assertThat(b.tasksDue()).isEqualTo(3);
        assertThat(b.tasksMissed()).isEqualTo(1);
        assertThat(b.planCompletionRate()).isEqualTo(0.333);
        assertThat(b.lateRate()).isEqualTo(1.0);
        assertThat(b.pomodoroCompletionRate()).isEqualTo(0.5);
        assertThat(b.avgInterruptions()).isEqualTo(1.0);
        assertThat(b.focusMinutes14()).isEqualTo(25);
        assertThat(b.preferredSlot()).isEqualTo("上午");
        assertThat(b.notes()).anySatisfy(n -> assertThat(n).contains("低于 50%"));
    }

    // ---------- helpers ----------

    void ev(Long node, ActivityType type, boolean correct, Long questionId, SelfConfidence sc, ErrorType et, LocalDate day) {
        Evidence e = new Evidence();
        ReflectionTestUtils.setField(e, "id", ids++);
        ReflectionTestUtils.setField(e, "createdAt", day.atTime(9, 0));
        e.setNodeId(node);
        e.setActivityType(type);
        e.setResult(correct ? EvidenceResult.CORRECT : EvidenceResult.INCORRECT);
        e.setScore(correct ? 1 : 0);
        e.setDifficulty(0.5);
        e.setQuestionId(questionId);
        e.setSelfConfidence(sc);
        e.setErrorType(et);
        e.setDurationSec(120);
        evidences.add(e);
    }

    void retest(Long node, String mode, boolean correct) {
        ScheduledRetest r = new ScheduledRetest();
        r.setNodeId(node);
        r.setReason(RetestReason.RETENTION_3D);
        r.setSourceMode(mode);
        r.setDone(true);
        r.setCorrect(correct);
        retests.add(r);
    }

    void task(LocalDate date, TaskStatus status, LocalDate doneOn) {
        PlanTask t = new PlanTask();
        t.setPlanDate(date);
        t.setTaskType(TaskType.QUIZ);
        t.setNodeId(1L);
        t.setStatus(status);
        t.setDoneAt(doneOn == null ? null : doneOn.atTime(20, 0));
        tasks.add(t);
    }

    void pomodoro(boolean completed, int interruptions) {
        PomodoroSession s = new PomodoroSession();
        s.setPlannedMinutes(25);
        s.setStartedAt(LocalDateTime.of(TODAY, java.time.LocalTime.of(8, 0)));
        s.setCompleted(completed);
        s.setInterruptions(interruptions);
        pomodoros.add(s);
    }

    static NodeMastery m(Long node, double mastery, double confidence) {
        NodeMastery m = new NodeMastery(7L, node);
        m.setMastery(mastery);
        m.setConfidence(confidence);
        return m;
    }
}
