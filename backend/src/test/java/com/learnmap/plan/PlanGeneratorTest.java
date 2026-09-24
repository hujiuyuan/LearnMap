package com.learnmap.plan;

import com.learnmap.graph.NodeType;
import com.learnmap.plan.PlanGenerator.Input;
import com.learnmap.plan.PlanGenerator.Plan;
import com.learnmap.plan.PlanGenerator.Settings;
import com.learnmap.plan.PlanGenerator.Task;
import com.learnmap.practice.LearnerGraphSnapshot;
import com.learnmap.practice.Snapshots;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanGeneratorTest {

    /** 2026-09-28 是周一 */
    static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    static Settings settings(GoalScope scope, Set<Long> chapters, int weekday, int weekend, MainStyle style, Rhythm rhythm) {
        return new Settings(scope, chapters, MONDAY.plusDays(20), weekday, weekend, style, rhythm);
    }

    static Plan plan(LearnerGraphSnapshot snap, Settings s) {
        return PlanGenerator.generate(new Input(snap, s, MONDAY, Map.of(), Map.of(), Set.of(), 0));
    }

    static List<Long> nodeOrder(Plan p) {
        return p.tasks().stream().map(Task::nodeId).filter(Objects::nonNull).distinct().toList();
    }

    @Test
    void prerequisitesComeFirstAndMasteredNodesAreSkipped() {
        // 1 → 2 → 3，4 独立；1 已掌握
        LearnerGraphSnapshot snap = new Snapshots()
                .node(1, NodeType.CONCEPT, 10).node(2, NodeType.CONCEPT, 10).node(3, NodeType.METHOD, 10)
                .node(4, NodeType.FACT, 10)
                .prereq(1, 2).prereq(2, 3)
                .mastery(1, 0.9, 0.9)
                .build();
        Plan p = plan(snap, settings(GoalScope.BOOK, Set.of(), 60, 60, MainStyle.UNDERSTAND, Rhythm.POMODORO));
        List<Long> order = nodeOrder(p);
        assertThat(order).doesNotContain(1L).containsExactlyInAnyOrder(2L, 3L, 4L);
        assertThat(order.indexOf(2L)).isLessThan(order.indexOf(3L));
        // 2 的下游多，比独立的 4 先学
        assertThat(order.get(0)).isEqualTo(2L);
        assertThat(p.warning()).isNull();
    }

    @Test
    void activitiesDependOnNodeTypeAndStyleAndCarryReasons() {
        LearnerGraphSnapshot snap = new Snapshots()
                .node(1, NodeType.FACT, 10).node(2, NodeType.CONCEPT, 10).node(3, NodeType.METHOD, 10)
                .node(4, NodeType.SYNTHESIS, 10)
                .build();
        Plan p = plan(snap, settings(GoalScope.BOOK, Set.of(), 120, 120, MainStyle.PRACTICE, Rhythm.POMODORO));
        assertThat(types(p, 1L)).containsExactly(TaskType.NEW_LEARN, TaskType.QUIZ);
        assertThat(types(p, 2L)).containsExactly(TaskType.NEW_LEARN, TaskType.FEYNMAN, TaskType.QUIZ);
        assertThat(types(p, 3L)).containsExactly(TaskType.NEW_LEARN, TaskType.DELIBERATE);
        assertThat(types(p, 4L)).containsExactly(TaskType.FIRST_PRINCIPLES);
        assertThat(p.tasks()).allSatisfy(t -> assertThat(t.reason()).isNotBlank());
    }

    @Test
    void scopeChaptersPullsInUnmasteredPrerequisitesFromOtherChapters() {
        LearnerGraphSnapshot snap = new Snapshots()
                .node(1, NodeType.FACT, 10).node(2, NodeType.FACT, 20).node(3, NodeType.FACT, 20)
                .prereq(1, 2)
                .build();
        Plan p = plan(snap, settings(GoalScope.CHAPTERS, Set.of(20L), 60, 60, MainStyle.MEMORIZE, Rhythm.MICRO));
        assertThat(nodeOrder(p)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void weakScopeOnlyTouchesStudiedButNotMasteredNodes() {
        LearnerGraphSnapshot snap = new Snapshots()
                .node(1, NodeType.FACT, 10).node(2, NodeType.FACT, 10).node(3, NodeType.FACT, 10)
                .mastery(2, 0.4, 0.5)
                .build();
        Plan p = plan(snap, settings(GoalScope.WEAK, Set.of(), 60, 60, MainStyle.MEMORIZE, Rhythm.POMODORO));
        assertThat(nodeOrder(p)).containsExactly(2L);
        // 掌握度 0.4 不算“新”，不再安排初学
        assertThat(types(p, 2L)).containsExactly(TaskType.QUIZ);
    }

    @Test
    void dueCardsAndRetestsGoFirstEachDayAndBudgetsAreRespected() {
        Snapshots b = new Snapshots();
        for (long i = 1; i <= 10; i++) {
            b.node(i, NodeType.CONCEPT, 10);
        }
        Input in = new Input(b.build(), settings(GoalScope.BOOK, Set.of(), 30, 60, MainStyle.UNDERSTAND, Rhythm.POMODORO),
                MONDAY, Map.of(MONDAY, 9), Map.of(MONDAY, 2), Set.of(), 0);
        Plan p = PlanGenerator.generate(in);
        List<Task> monday = p.tasks().stream().filter(t -> t.date().equals(MONDAY)).toList();
        assertThat(monday.get(0).type()).isEqualTo(TaskType.REVIEW_CARDS);
        assertThat(monday.get(0).cardCount()).isEqualTo(9);
        assertThat(monday.get(0).minutes()).isEqualTo(5);
        assertThat(monday.get(1).type()).isEqualTo(TaskType.RETEST);
        assertThat(monday.get(1).minutes()).isEqualTo(4);
        // 每个 CONCEPT 节点 20 分钟：周一 30 − 9 = 21，装一个；周六是第 6 天，先放 10 分钟交错练习，再装两个
        assertThat(monday.stream().map(Task::nodeId).filter(Objects::nonNull).distinct()).hasSize(1);
        List<Task> saturday = p.tasks().stream().filter(t -> t.date().equals(MONDAY.plusDays(5))).toList();
        assertThat(saturday.get(0).type()).isEqualTo(TaskType.INTERLEAVED);
        assertThat(saturday.stream().map(Task::nodeId).filter(Objects::nonNull).distinct()).hasSize(2);
        // 周一 5 + 4 + 20 = 29 分钟，第二个番茄开始于放不下的那一项
        assertThat(monday.stream().mapToInt(Task::blockNo).max().orElseThrow()).isEqualTo(2);
    }

    @Test
    void warnsWhenTheGoalCannotBeFinishedBeforeTheDeadline() {
        Snapshots b = new Snapshots();
        for (long i = 1; i <= 30; i++) {
            b.node(i, NodeType.SYNTHESIS, 10);
        }
        Settings s = new Settings(GoalScope.BOOK, Set.of(), MONDAY.plusDays(2), 20, 20, MainStyle.PRACTICE, Rhythm.LONG);
        Plan p = plan(b.build(), s);
        assertThat(p.warning()).isEqualTo(PlanGenerator.BEHIND_WARNING);
        assertThat(p.tasks()).extracting(Task::date).allSatisfy(d -> assertThat(d).isBeforeOrEqualTo(MONDAY.plusDays(2)));
    }

    @Test
    void replanningSkipsWhatIsAlreadyDone() {
        LearnerGraphSnapshot snap = new Snapshots().node(1, NodeType.FACT, 10).build();
        Input in = new Input(snap, settings(GoalScope.BOOK, Set.of(), 30, 30, MainStyle.MEMORIZE, Rhythm.POMODORO),
                MONDAY, Map.of(), Map.of(), Set.of(PlanGenerator.doneKey(1L, TaskType.NEW_LEARN)), 10);
        assertThat(types(PlanGenerator.generate(in), 1L)).containsExactly(TaskType.QUIZ);
    }

    @Test
    void rejectsDeadlineInThePast() {
        Settings s = new Settings(GoalScope.BOOK, Set.of(), MONDAY.minusDays(1), 30, 30, MainStyle.MEMORIZE, Rhythm.POMODORO);
        assertThatThrownBy(() -> plan(new Snapshots().node(1, NodeType.FACT, 10).build(), s))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static List<TaskType> types(Plan p, Long node) {
        return p.tasks().stream().filter(t -> node.equals(t.nodeId())).map(Task::type).toList();
    }
}
