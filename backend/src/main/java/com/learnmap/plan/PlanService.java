package com.learnmap.plan;

import com.learnmap.card.ReviewCard;
import com.learnmap.card.ReviewCardRepository;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.graph.GraphKind;
import com.learnmap.graph.GraphService;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.plan.PlanGenerator.Input;
import com.learnmap.plan.PlanGenerator.Plan;
import com.learnmap.plan.PlanGenerator.Settings;
import com.learnmap.practice.LearnerGraphSnapshot;
import com.learnmap.practice.LearnerGraphSnapshotLoader;
import com.learnmap.retest.ScheduledRetest;
import com.learnmap.retest.ScheduledRetestRepository;
import com.learnmap.user.AppUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习目标 → 计划。创建目标时排一次；每天夜里按最新掌握度、到期卡片和复测重排一次；学习者也可以手动重排。
 * 重排只替换今天及以后还没做的任务，做过的保留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final StudyGoalRepository goals;
    private final PlanTaskRepository tasks;
    private final GraphService graphs;
    private final LearnerGraphSnapshotLoader snapshots;
    private final ReviewCardRepository cards;
    private final ScheduledRetestRepository retests;
    private final UserService users;
    private final Clock clock;

    public record GoalRequest(Long graphId, GoalScope scope, List<Long> chapterIds, LocalDate deadline,
                              int weekdayMinutes, int weekendMinutes, MainStyle mainStyle, Rhythm rhythm) {
    }

    public record GoalView(Long id, Long learnerId, Long graphId, GoalScope scope, List<Long> chapterIds,
                           LocalDate deadline, int weekdayMinutes, int weekendMinutes, MainStyle mainStyle,
                           Rhythm rhythm, String warning, LocalDateTime plannedAt) {
        static GoalView of(StudyGoal g) {
            return new GoalView(g.getId(), g.getLearnerId(), g.getGraphId(), g.getScope(), g.chapters(), g.getDeadline(),
                    g.getWeekdayMinutes(), g.getWeekendMinutes(), g.getMainStyle(), g.getRhythm(), g.getWarning(),
                    g.getPlannedAt());
        }
    }

    public record TaskView(Long id, TaskType type, Long nodeId, String nodeName, Integer cardCount, int minutes,
                           String reason, TaskStatus status) {
    }

    public record BlockView(int blockNo, int minutes, List<TaskView> tasks) {
    }

    public record DaySummary(LocalDate date, int minutes, int tasks) {
    }

    public record DayPlan(GoalView goal, LocalDate date, int totalMinutes, int doneMinutes, List<BlockView> blocks,
                          List<DaySummary> upcoming) {
    }

    // ---------- 目标 ----------

    /** 学习者本人或他的辅导者设目标；一个学习者同时只有一个进行中的目标 */
    @Transactional
    public DayPlan createGoal(AppUser actor, GoalRequest req) {
        KnowledgeGraph g = graphs.require(req.graphId());
        if (g.getKind() != GraphKind.LEARNER_COPY) {
            throw new IllegalArgumentException("学习目标要设在学习者自己的图谱上");
        }
        Long learnerId = g.getOwnerLearnerId();
        if (!actor.getId().equals(learnerId) && !users.isTutorOf(actor.getId(), learnerId)) {
            throw new ForbiddenException("只有学习者本人或他的辅导者可以设学习目标");
        }
        if (req.scope() == GoalScope.CHAPTERS && (req.chapterIds() == null || req.chapterIds().isEmpty())) {
            throw new IllegalArgumentException("请选择要学的章");
        }
        if (req.weekdayMinutes() < 0 || req.weekendMinutes() < 0 || req.weekdayMinutes() + req.weekendMinutes() == 0) {
            throw new IllegalArgumentException("每天可用的学习时间要大于 0");
        }
        if (req.deadline().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("截止日期已经过了");
        }
        for (StudyGoal old : goals.findByLearnerIdAndActiveTrue(learnerId)) {
            old.setActive(false);
            tasks.deleteByGoalIdAndStatusAndPlanDateGreaterThanEqual(old.getId(), TaskStatus.TODO, LocalDate.now(clock));
        }
        StudyGoal goal = new StudyGoal();
        goal.setLearnerId(learnerId);
        goal.setGraphId(g.getId());
        goal.setScope(req.scope());
        goal.setChapterIds(req.scope() == GoalScope.CHAPTERS
                ? req.chapterIds().stream().map(String::valueOf).collect(Collectors.joining(",")) : null);
        goal.setDeadline(req.deadline());
        goal.setWeekdayMinutes(req.weekdayMinutes());
        goal.setWeekendMinutes(req.weekendMinutes());
        goal.setMainStyle(req.mainStyle() == null ? MainStyle.UNDERSTAND : req.mainStyle());
        goal.setRhythm(req.rhythm() == null ? Rhythm.POMODORO : req.rhythm());
        goal.setActive(true);
        goal.setCreatedBy(actor.getId());
        goal = goals.save(goal);
        replan(goal);
        return day(goal, LocalDate.now(clock));
    }

    public List<GoalView> goalsOf(AppUser actor, Long learnerId) {
        users.checkCanView(actor, learnerId);
        return goals.findByLearnerIdAndActiveTrue(learnerId).stream().map(GoalView::of).toList();
    }

    // ---------- 计划 ----------

    @Transactional(readOnly = true)
    public DayPlan today(AppUser actor, Long learnerId, LocalDate date) {
        users.checkCanView(actor, learnerId);
        StudyGoal goal = goals.findFirstByLearnerIdAndActiveTrueOrderByIdDesc(learnerId).orElse(null);
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        if (goal == null) {
            return new DayPlan(null, d, 0, 0, List.of(), List.of());
        }
        return day(goal, d);
    }

    @Transactional
    public DayPlan replan(AppUser actor, Long learnerId) {
        StudyGoal goal = goals.findFirstByLearnerIdAndActiveTrueOrderByIdDesc(learnerId)
                .orElseThrow(() -> new IllegalStateException("还没有设学习目标"));
        if (!actor.getId().equals(learnerId) && !users.isTutorOf(actor.getId(), learnerId)) {
            throw new ForbiddenException("只有学习者本人或他的辅导者可以重排计划");
        }
        replan(goal);
        return day(goal, LocalDate.now(clock));
    }

    @Transactional
    public TaskView mark(AppUser actor, Long taskId, TaskStatus status) {
        PlanTask t = tasks.findById(taskId).orElseThrow(() -> NotFoundException.of("任务", taskId));
        if (!t.getLearnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能完成自己的任务");
        }
        t.setStatus(status);
        t.setDoneAt(status == TaskStatus.TODO ? null : LocalDateTime.now(clock));
        return view(t, names(goals.findById(t.getGoalId()).orElseThrow()));
    }

    /** 每天凌晨 3 点按最新掌握度重排所有进行中的目标；已过截止日期的目标自动结束 */
    @Scheduled(cron = "${learnmap.plan.replan-cron:0 0 3 * * *}")
    @Transactional
    public void nightlyReplan() {
        LocalDate today = LocalDate.now(clock);
        for (StudyGoal g : goals.findByActiveTrue()) {
            try {
                if (g.getDeadline().isBefore(today)) {
                    g.setActive(false);
                } else {
                    replan(g);
                }
            } catch (RuntimeException e) {
                log.warn("重排学习目标 {} 失败", g.getId(), e);
            }
        }
    }

    void replan(StudyGoal goal) {
        LocalDate today = LocalDate.now(clock);
        tasks.deleteByGoalIdAndStatusAndPlanDateGreaterThanEqual(goal.getId(), TaskStatus.TODO, today);
        tasks.flush();
        KnowledgeGraph g = graphs.require(goal.getGraphId());
        LearnerGraphSnapshot snap = snapshots.load(g);

        Set<String> done = new HashSet<>();
        int doneToday = 0;
        for (PlanTask t : tasks.findByGoalIdAndStatus(goal.getId(), TaskStatus.DONE)) {
            if (t.getNodeId() != null) {
                done.add(PlanGenerator.doneKey(t.getNodeId(), t.getTaskType()));
            }
            if (today.equals(t.getPlanDate())) {
                doneToday += t.getMinutes();
                if (t.getNodeId() == null) {
                    done.add(PlanGenerator.doneKey(null, t.getTaskType()));
                }
            }
        }
        LocalDate end = today.plusDays(PlanGenerator.HORIZON_DAYS);
        Map<LocalDate, Integer> cardsDue = new HashMap<>();
        for (ReviewCard c : cards.findByLearnerIdAndDueAtBefore(goal.getLearnerId(), end.atStartOfDay())) {
            LocalDate d = c.getDueAt().toLocalDate();
            cardsDue.merge(d.isBefore(today) ? today : d, 1, Integer::sum);
        }
        Map<LocalDate, Integer> retestsDue = new HashMap<>();
        for (ScheduledRetest r : retests.findByLearnerIdAndDoneFalseOrderByDueAtAsc(goal.getLearnerId())) {
            LocalDate d = r.getDueAt().toLocalDate();
            if (d.isBefore(end)) {
                retestsDue.merge(d.isBefore(today) ? today : d, 1, Integer::sum);
            }
        }
        Settings settings = new Settings(goal.getScope(), new HashSet<>(goal.chapters()), goal.getDeadline(),
                goal.getWeekdayMinutes(), goal.getWeekendMinutes(), goal.getMainStyle(), goal.getRhythm());
        Plan plan = PlanGenerator.generate(new Input(snap, settings, today, cardsDue, retestsDue, done, doneToday));

        // 今天已经做完的任务占了前面的块，新任务的块号接在后面
        int blockOffset = tasks.findByGoalIdAndPlanDateOrderByBlockNoAscOrdinalAsc(goal.getId(), today).stream()
                .mapToInt(PlanTask::getBlockNo).max().orElse(0);
        List<PlanTask> out = new ArrayList<>();
        for (PlanGenerator.Task t : plan.tasks()) {
            PlanTask pt = new PlanTask();
            pt.setGoalId(goal.getId());
            pt.setLearnerId(goal.getLearnerId());
            pt.setPlanDate(t.date());
            pt.setBlockNo(t.blockNo() + (t.date().equals(today) ? blockOffset : 0));
            pt.setOrdinal(t.ordinal());
            pt.setTaskType(t.type());
            pt.setNodeId(t.nodeId());
            pt.setCardCount(t.cardCount());
            pt.setMinutes(t.minutes());
            pt.setReason(t.reason());
            pt.setStatus(TaskStatus.TODO);
            out.add(pt);
        }
        tasks.saveAll(out);
        goal.setWarning(plan.warning());
        goal.setPlannedAt(LocalDateTime.now(clock));
    }

    private DayPlan day(StudyGoal goal, LocalDate date) {
        Map<Long, String> names = names(goal);
        List<PlanTask> ts = tasks.findByGoalIdAndPlanDateOrderByBlockNoAscOrdinalAsc(goal.getId(), date);
        Map<Integer, List<TaskView>> byBlock = new LinkedHashMap<>();
        for (PlanTask t : ts) {
            byBlock.computeIfAbsent(t.getBlockNo(), k -> new ArrayList<>()).add(view(t, names));
        }
        List<BlockView> blocks = byBlock.entrySet().stream()
                .map(e -> new BlockView(e.getKey(), e.getValue().stream().mapToInt(TaskView::minutes).sum(), e.getValue()))
                .toList();
        int total = ts.stream().mapToInt(PlanTask::getMinutes).sum();
        int done = ts.stream().filter(t -> t.getStatus() == TaskStatus.DONE).mapToInt(PlanTask::getMinutes).sum();
        Map<LocalDate, List<PlanTask>> later = tasks
                .findByGoalIdAndPlanDateBetweenOrderByPlanDateAscBlockNoAscOrdinalAsc(goal.getId(), date.plusDays(1), date.plusDays(6))
                .stream().collect(Collectors.groupingBy(PlanTask::getPlanDate, LinkedHashMap::new, Collectors.toList()));
        List<DaySummary> upcoming = later.entrySet().stream()
                .map(e -> new DaySummary(e.getKey(), e.getValue().stream().mapToInt(PlanTask::getMinutes).sum(), e.getValue().size()))
                .toList();
        return new DayPlan(GoalView.of(goal), date, total, done, blocks, upcoming);
    }

    private Map<Long, String> names(StudyGoal goal) {
        LearnerGraphSnapshot snap = snapshots.load(graphs.require(goal.getGraphId()));
        Map<Long, String> names = new HashMap<>();
        snap.nodes().forEach((id, n) -> names.put(id, n.getName()));
        return names;
    }

    private TaskView view(PlanTask t, Map<Long, String> names) {
        return new TaskView(t.getId(), t.getTaskType(), t.getNodeId(), t.getNodeId() == null ? null : names.get(t.getNodeId()),
                t.getCardCount(), t.getMinutes(), t.getReason(), t.getStatus());
    }
}
