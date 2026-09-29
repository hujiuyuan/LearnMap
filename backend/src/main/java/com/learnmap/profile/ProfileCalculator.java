package com.learnmap.profile;

import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.ErrorType;
import com.learnmap.evidence.Evidence;
import com.learnmap.evidence.EvidenceResult;
import com.learnmap.evidence.SelfConfidence;
import com.learnmap.feynman.FeynmanAttempt;
import com.learnmap.graph.NodeType;
import com.learnmap.mastery.MasteryEngine;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.plan.PlanTask;
import com.learnmap.plan.PomodoroSession;
import com.learnmap.plan.TaskStatus;
import com.learnmap.plan.TaskType;
import com.learnmap.practice.PracticeSession;
import com.learnmap.profile.LearnerProfile.AbilityStat;
import com.learnmap.profile.LearnerProfile.Abilities;
import com.learnmap.profile.LearnerProfile.Behavior;
import com.learnmap.profile.LearnerProfile.Calibration;
import com.learnmap.profile.LearnerProfile.ChapterStat;
import com.learnmap.profile.LearnerProfile.Completion;
import com.learnmap.profile.LearnerProfile.ErrorStat;
import com.learnmap.profile.LearnerProfile.Errors;
import com.learnmap.profile.LearnerProfile.Highlights;
import com.learnmap.profile.LearnerProfile.Knowledge;
import com.learnmap.profile.LearnerProfile.ModeStat;
import com.learnmap.profile.LearnerProfile.Modes;
import com.learnmap.profile.LearnerProfile.NodeStat;
import com.learnmap.profile.LearnerProfile.SolutionUse;
import com.learnmap.question.Ability;
import com.learnmap.question.Question;
import com.learnmap.retest.RetestReason;
import com.learnmap.retest.ScheduledRetest;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 从证据、复测、计划、番茄钟等原始记录算出五张画像（纯逻辑，便于测试） */
public final class ProfileCalculator {

    /** 没记录用时的证据按 1 分钟算 */
    static final int DEFAULT_SECONDS = 60;
    static final int MIN_RETESTS_FOR_SUGGESTION = 3;
    static final int MIN_EVIDENCE_FOR_SUGGESTION = 5;

    private static final Map<String, String> MODE_LABELS = Map.ofEntries(
            Map.entry(ActivityType.CARD.name(), "间隔重复"),
            Map.entry(ActivityType.FEYNMAN.name(), "费曼讲解"),
            Map.entry(ActivityType.PRACTICE.name(), "刻意练习 / 第一性原理"),
            Map.entry(ActivityType.INTERLEAVED.name(), "交错练习"),
            Map.entry(ActivityType.QUIZ.name(), "小测"),
            Map.entry(ActivityType.MEMORY_PALACE.name(), "记忆宫殿"),
            Map.entry("DELIBERATE", "刻意练习"),
            Map.entry("FIRST_PRINCIPLES", "第一性原理"),
            Map.entry(TaskType.REVIEW_CARDS.name(), "计划：复习卡片"),
            Map.entry(TaskType.RETEST.name(), "计划：延时复测"),
            Map.entry(TaskType.NEW_LEARN.name(), "计划：初学"),
            Map.entry("PLAN_" + TaskType.QUIZ.name(), "计划：小测"),
            Map.entry("PLAN_" + TaskType.FEYNMAN.name(), "计划：费曼讲解"),
            Map.entry("PLAN_" + TaskType.DELIBERATE.name(), "计划：刻意练习"),
            Map.entry("PLAN_" + TaskType.INTERLEAVED.name(), "计划：交错练习"),
            Map.entry("PLAN_" + TaskType.FIRST_PRINCIPLES.name(), "计划：第一性原理"));

    private static final Map<ErrorType, String> ERROR_LABELS = Map.of(
            ErrorType.KNOWLEDGE_GAP, "知识不会", ErrorType.CONCEPT_CONFUSION, "概念混淆", ErrorType.CALCULATION, "计算",
            ErrorType.READING, "审题", ErrorType.CARELESS, "粗心", ErrorType.WRONG_METHOD, "方法选错");

    private static final Map<NodeType, String> TYPE_LABELS = Map.of(
            NodeType.FACT, "事实类", NodeType.CONCEPT, "概念类", NodeType.PRINCIPLE, "原理 / 公式类",
            NodeType.METHOD, "方法类", NodeType.SYNTHESIS, "综合应用类");

    private ProfileCalculator() {
    }

    public record NodeInfo(Long id, Long graphId, Long chapterId, String chapterTitle, String name, NodeType type) {
    }

    public record Input(Long learnerId, LocalDate today, Map<Long, NodeInfo> nodes, Map<Long, NodeMastery> mastery,
                        List<Evidence> evidences, Map<Long, Question> questions, Map<Long, String> solutionNames,
                        List<FeynmanAttempt> feynman, List<ScheduledRetest> retests, List<PlanTask> tasks,
                        List<PomodoroSession> pomodoros, List<PracticeSession> practice) {
    }

    public static LearnerProfile compute(Input in) {
        Modes modes = modes(in);
        return new LearnerProfile(in.learnerId(), in.today(), knowledge(in), abilities(in), errors(in), modes, behavior(in), null);
    }

    public static Highlights highlights(LearnerProfile p) {
        String best = p.modes().suggestions().isEmpty() ? null : p.modes().suggestions().get(0);
        return new Highlights(p.knowledge().mastered(), p.knowledge().total(), p.behavior().streak(),
                p.behavior().activeDays14(), best);
    }

    // ---------- 知识 ----------

    static Knowledge knowledge(Input in) {
        List<NodeStat> nodes = new ArrayList<>();
        Map<List<Object>, List<NodeStat>> byChapter = new LinkedHashMap<>();
        Map<List<Object>, String> titles = new HashMap<>();
        int[] c = new int[NodeStatus.values().length];
        for (NodeInfo n : in.nodes().values()) {
            NodeMastery m = in.mastery().get(n.id());
            NodeStatus st = NodeStatus.of(m);
            c[st.ordinal()]++;
            NodeStat s = new NodeStat(n.id(), n.graphId(), n.chapterId(), n.name(), n.type(),
                    m == null ? null : m.getMastery(), m == null ? null : m.getConfidence(), st);
            nodes.add(s);
            List<Object> key = List.of(n.graphId(), n.chapterId() == null ? 0L : n.chapterId());
            byChapter.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            titles.putIfAbsent(key, n.chapterTitle() == null ? "其他" : n.chapterTitle());
        }
        List<ChapterStat> chapters = byChapter.entrySet().stream().map(e -> {
            List<NodeStat> ns = e.getValue();
            return new ChapterStat((Long) e.getKey().get(0), (Long) e.getKey().get(1), titles.get(e.getKey()), ns.size(),
                    (int) ns.stream().filter(s -> s.status() == NodeStatus.MASTERED).count(),
                    round(ns.stream().mapToDouble(s -> s.mastery() == null ? 0 : s.mastery()).average().orElse(0)));
        }).toList();
        return new Knowledge(nodes.size(), c[NodeStatus.MASTERED.ordinal()], c[NodeStatus.LEARNING.ordinal()],
                c[NodeStatus.WEAK.ordinal()], c[NodeStatus.UNSURE.ordinal()], c[NodeStatus.UNTOUCHED.ordinal()], chapters, nodes);
    }

    // ---------- 能力 ----------

    static Abilities abilities(Input in) {
        Map<Ability, int[]> stats = new EnumMap<>(Ability.class);
        Map<String, Integer> solutions = new LinkedHashMap<>();
        int total = 0;
        int over = 0;
        int under = 0;
        for (Evidence e : in.evidences()) {
            if (e.getQuestionId() == null) {
                continue;
            }
            boolean correct = e.getResult() == EvidenceResult.CORRECT;
            Question q = in.questions().get(e.getQuestionId());
            if (q != null) {
                for (Ability a : q.abilityList()) {
                    int[] s = stats.computeIfAbsent(a, k -> new int[2]);
                    s[0]++;
                    if (correct) {
                        s[1]++;
                    }
                }
            }
            if (e.getSelfConfidence() != null) {
                total++;
                if (e.getSelfConfidence() == SelfConfidence.SURE && !correct) {
                    over++;
                } else if (e.getSelfConfidence() != SelfConfidence.SURE && correct) {
                    under++;
                }
            }
            if (correct && e.getSolutionId() != null) {
                String name = in.solutionNames().getOrDefault(e.getSolutionId(), "其他解法");
                solutions.merge(name, 1, Integer::sum);
            }
        }
        List<AbilityStat> abilities = stats.entrySet().stream()
                .map(e -> new AbilityStat(e.getKey(), e.getKey().label, e.getValue()[0], round(e.getValue()[1] / (double) e.getValue()[0])))
                .toList();
        List<FeynmanAttempt> graded = in.feynman();
        Double expression = graded.isEmpty() ? null : round(graded.stream()
                .mapToDouble(a -> a.getReviewedScore() != null ? a.getReviewedScore() : a.getScore()).average().orElse(0));
        Calibration cal = new Calibration(total, over, under, total == 0 ? null : round((total - over - under) / (double) total));
        List<SolutionUse> sols = solutions.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new SolutionUse(e.getKey(), e.getValue())).toList();
        return new Abilities(abilities, expression, graded.size(), cal, sols);
    }

    // ---------- 错误 ----------

    static Errors errors(Input in) {
        Map<ErrorType, Integer> counts = new EnumMap<>(ErrorType.class);
        int wrong = 0;
        int unlabeled = 0;
        for (Evidence e : in.evidences()) {
            if (e.getResult() != EvidenceResult.INCORRECT || e.getQuestionId() == null) {
                continue;
            }
            wrong++;
            if (e.getErrorType() == null) {
                unlabeled++;
            } else {
                counts.merge(e.getErrorType(), 1, Integer::sum);
            }
        }
        int labeled = wrong - unlabeled;
        List<ErrorStat> stats = counts.entrySet().stream()
                .sorted(Map.Entry.<ErrorType, Integer>comparingByValue().reversed())
                .map(e -> new ErrorStat(e.getKey().name(), ERROR_LABELS.get(e.getKey()), e.getValue(),
                        round(e.getValue() / (double) labeled)))
                .toList();
        return new Errors(wrong, unlabeled, stats);
    }

    // ---------- 学习方式 ----------

    private static final Set<ActivityType> MODES = Set.of(ActivityType.CARD, ActivityType.FEYNMAN, ActivityType.PRACTICE,
            ActivityType.INTERLEAVED, ActivityType.QUIZ, ActivityType.MEMORY_PALACE);

    static Modes modes(Input in) {
        record Key(String mode, NodeType type) {
        }
        Map<Key, double[]> acc = new LinkedHashMap<>(); // evidences, seconds, gain
        Map<Long, List<Evidence>> byNode = in.evidences().stream()
                .collect(Collectors.groupingBy(Evidence::getNodeId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<Evidence>> e : byNode.entrySet()) {
            NodeInfo node = in.nodes().get(e.getKey());
            if (node == null) {
                continue;
            }
            MasteryEngine.State state = MasteryEngine.State.EMPTY;
            List<Evidence> list = new ArrayList<>(e.getValue());
            list.sort(Comparator.comparing(Evidence::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            for (Evidence ev : list) {
                MasteryEngine.State next = MasteryEngine.update(state, ev.getScore(), ev.getDifficulty(), ev.getSelfConfidence(), true);
                if (MODES.contains(ev.getActivityType())) {
                    double[] a = acc.computeIfAbsent(new Key(ev.getActivityType().name(), node.type()), k -> new double[5]);
                    a[0]++;
                    a[1] += ev.getDurationSec() == null ? DEFAULT_SECONDS : ev.getDurationSec();
                    a[2] += next.mastery() - state.mastery();
                }
                state = next;
            }
        }
        for (ScheduledRetest r : in.retests()) {
            if (!r.isDone() || r.getSourceMode() == null || !isRetention(r.getReason())) {
                continue;
            }
            NodeInfo node = in.nodes().get(r.getNodeId());
            if (node == null) {
                continue;
            }
            double[] a = acc.computeIfAbsent(new Key(r.getSourceMode(), node.type()), k -> new double[5]);
            a[3]++;
            if (Boolean.TRUE.equals(r.getCorrect())) {
                a[4]++;
            }
        }
        List<ModeStat> stats = acc.entrySet().stream().map(e -> {
            double[] a = e.getValue();
            double minutes = a[1] / 60.0;
            return new ModeStat(e.getKey().mode(), MODE_LABELS.getOrDefault(e.getKey().mode(), e.getKey().mode()),
                    e.getKey().type(), (int) a[0], round(minutes), minutes > 0 ? round(a[2] / minutes * 10) : null,
                    (int) a[3], (int) a[4], a[3] > 0 ? round(a[4] / a[3]) : null);
        }).sorted(Comparator.comparing(ModeStat::nodeType).thenComparing(ModeStat::mode)).toList();
        return new Modes(stats, completion(in), suggestions(stats));
    }

    private static boolean isRetention(RetestReason r) {
        return r == RetestReason.RETENTION_3D || r == RetestReason.RETENTION_7D || r == RetestReason.RETENTION_21D;
    }

    static List<Completion> completion(Input in) {
        List<Completion> out = new ArrayList<>();
        Map<String, int[]> practice = new LinkedHashMap<>();
        for (PracticeSession s : in.practice()) {
            int[] a = practice.computeIfAbsent(s.getMode().name(), k -> new int[2]);
            a[0]++;
            if (s.getStatus() == PracticeSession.Status.COMPLETED) {
                a[1]++;
            }
        }
        practice.forEach((mode, a) -> out.add(new Completion(mode, MODE_LABELS.getOrDefault(mode, mode), a[0], a[1], rate(a[1], a[0]))));
        Map<String, int[]> plan = new LinkedHashMap<>();
        for (PlanTask t : in.tasks()) {
            if (!isDue(t, in.today())) {
                continue;
            }
            String key = t.getNodeId() == null ? t.getTaskType().name()
                    : t.getTaskType() == TaskType.NEW_LEARN ? TaskType.NEW_LEARN.name() : "PLAN_" + t.getTaskType().name();
            int[] a = plan.computeIfAbsent(key, k -> new int[2]);
            a[0]++;
            if (t.getStatus() == TaskStatus.DONE) {
                a[1]++;
            }
        }
        plan.forEach((mode, a) -> out.add(new Completion(mode, MODE_LABELS.getOrDefault(mode, mode), a[0], a[1], rate(a[1], a[0]))));
        return out;
    }

    /** 每类节点选效果最好的方式：先看延时保持率（至少 3 次复测），不够再看单位时间提升（至少 5 条证据） */
    static List<String> suggestions(List<ModeStat> stats) {
        List<String> out = new ArrayList<>();
        Map<NodeType, List<ModeStat>> byType = stats.stream()
                .collect(Collectors.groupingBy(ModeStat::nodeType, () -> new EnumMap<>(NodeType.class), Collectors.toList()));
        byType.forEach((type, list) -> {
            ModeStat best = list.stream().filter(s -> s.retests() >= MIN_RETESTS_FOR_SUGGESTION)
                    .max(Comparator.comparingDouble(ModeStat::retentionRate)).orElse(null);
            if (best != null) {
                out.add(TYPE_LABELS.get(type) + "知识点你用「" + best.modeLabel() + "」效果最好：学会后复测还记得 "
                        + percent(best.retentionRate()) + "（" + best.retests() + " 次复测）");
                return;
            }
            best = list.stream().filter(s -> s.evidences() >= MIN_EVIDENCE_FOR_SUGGESTION && s.gainPer10Min() != null)
                    .max(Comparator.comparingDouble(ModeStat::gainPer10Min)).orElse(null);
            if (best != null && best.gainPer10Min() > 0) {
                out.add(TYPE_LABELS.get(type) + "知识点你用「" + best.modeLabel() + "」进步最快（复测数据还不够，结论待验证）");
            }
        });
        return out;
    }

    // ---------- 学习行为 ----------

    static Behavior behavior(Input in) {
        LocalDate today = in.today();
        Set<LocalDate> activeDays = new TreeSet<>();
        int[] hours = new int[24];
        for (Evidence e : in.evidences()) {
            if (e.getCreatedAt() != null) {
                activeDays.add(e.getCreatedAt().toLocalDate());
                hours[e.getCreatedAt().getHour()]++;
            }
        }
        int active14 = (int) activeDays.stream().filter(d -> !d.isAfter(today) && d.isAfter(today.minusDays(14))).count();
        int streak = 0;
        LocalDate d = activeDays.contains(today) ? today : today.minusDays(1);
        while (activeDays.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }

        int due = 0;
        int done = 0;
        int skipped = 0;
        int missed = 0;
        int late = 0;
        for (PlanTask t : in.tasks()) {
            if (!isDue(t, today)) {
                continue;
            }
            due++;
            switch (t.getStatus()) {
                case DONE -> {
                    done++;
                    if (t.getDoneAt() != null && t.getDoneAt().toLocalDate().isAfter(t.getPlanDate())) {
                        late++;
                    }
                }
                case SKIPPED -> skipped++;
                case TODO -> missed++;
            }
        }

        int started = in.pomodoros().size();
        int completed = (int) in.pomodoros().stream().filter(PomodoroSession::isCompleted).count();
        Double avgInterruptions = started == 0 ? null
                : round(in.pomodoros().stream().mapToInt(PomodoroSession::getInterruptions).average().orElse(0));
        int focus14 = in.pomodoros().stream()
                .filter(p -> p.isCompleted() && p.getStartedAt() != null
                        && ChronoUnit.DAYS.between(p.getStartedAt().toLocalDate(), today) < 14)
                .mapToInt(PomodoroSession::getPlannedMinutes).sum();

        Double planRate = rate(done, due);
        Double lateRate = rate(late, done);
        List<String> notes = new ArrayList<>();
        if (planRate != null && planRate < 0.5) {
            notes.add("计划完成率低于 50%，计划可能太重，建议减少每天的学习时间或缩小范围");
        }
        if (due > 0 && skipped / (double) due > 0.3) {
            notes.add("经常跳过任务，可以看看是不是某类活动不合适");
        }
        if (lateRate != null && lateRate > 0.3) {
            notes.add("常把任务拖到计划日之后才完成");
        }
        if (started >= 3 && completed / (double) started < 0.5) {
            notes.add("番茄钟一半以上没坚持完，可以试试 10 分钟的碎片节奏");
        }
        if (notes.isEmpty() && due > 0) {
            notes.add("按计划完成得不错");
        }
        return new Behavior(active14, streak, planRate, due, done, skipped, missed, lateRate, started, completed,
                rate(completed, started), avgInterruptions, focus14, preferredSlot(hours), toList(hours), notes);
    }

    private static boolean isDue(PlanTask t, LocalDate today) {
        return t.getPlanDate().isBefore(today) || (t.getPlanDate().equals(today) && t.getStatus() != TaskStatus.TODO);
    }

    static String preferredSlot(int[] hours) {
        String[] names = {"深夜", "上午", "下午", "晚上"};
        int[] slots = new int[4];
        for (int h = 0; h < 24; h++) {
            slots[h < 5 ? 0 : h < 12 ? 1 : h < 18 ? 2 : 3] += hours[h];
        }
        int best = -1;
        for (int i = 0; i < 4; i++) {
            if (slots[i] > 0 && (best < 0 || slots[i] > slots[best])) {
                best = i;
            }
        }
        return best < 0 ? null : names[best];
    }

    private static List<Integer> toList(int[] a) {
        List<Integer> out = new ArrayList<>(a.length);
        for (int v : a) {
            out.add(v);
        }
        return Collections.unmodifiableList(out);
    }

    private static Double rate(int part, int whole) {
        return whole == 0 ? null : round(part / (double) whole);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static String percent(double v) {
        return Math.round(v * 100) + "%";
    }
}
