package com.learnmap.plan;

import com.learnmap.graph.NodeType;
import com.learnmap.practice.LearnerGraphSnapshot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 排学习计划（纯逻辑，便于测试）：
 * <ol>
 *   <li>按目标范围选出没掌握的节点，并把它们没掌握的前置也拉进来；</li>
 *   <li>按前置关系拓扑排序，同时可学的节点里先学“薄弱 + 影响面大”的；</li>
 *   <li>按节点类型 × 主风格给每个节点配活动，并写下理由；</li>
 *   <li>每天先放到期卡片和复测，再按当天可用时间装节点，每 3 天插一次交错练习；</li>
 *   <li>按学习节奏切成番茄 / 微学习 / 整块。</li>
 * </ol>
 * 只排未来 {@value #HORIZON_DAYS} 天，之后每天夜里按最新掌握度重排。
 */
public final class PlanGenerator {

    public static final int HORIZON_DAYS = 14;
    public static final String BEHIND_WARNING = "按当前进度考前学不完，建议缩小范围或增加每天的学习时间";

    private PlanGenerator() {
    }

    public record Settings(GoalScope scope, Set<Long> chapters, LocalDate deadline, int weekdayMinutes,
                           int weekendMinutes, MainStyle style, Rhythm rhythm) {
    }

    /**
     * @param cardsDue   每天到期的卡片数（今天包含已过期的）
     * @param retestsDue 每天到期的复测数
     * @param done       已完成的任务：键为 "节点 id:任务类型"，重排时不再重复安排
     * @param minutesDoneToday 今天已经完成的分钟数，从今天的预算里扣掉
     */
    public record Input(LearnerGraphSnapshot snapshot, Settings settings, LocalDate today,
                        Map<LocalDate, Integer> cardsDue, Map<LocalDate, Integer> retestsDue,
                        Set<String> done, int minutesDoneToday) {
    }

    public record Task(LocalDate date, int blockNo, int ordinal, TaskType type, Long nodeId, Integer cardCount,
                       int minutes, String reason) {
    }

    public record Plan(List<Task> tasks, String warning, int remainingNodes) {
    }

    record Unit(TaskType type, Long nodeId, int minutes, String reason, Integer cardCount) {
        Unit(TaskType type, Long nodeId, int minutes, String reason) {
            this(type, nodeId, minutes, reason, null);
        }
    }

    public static String doneKey(Long nodeId, TaskType type) {
        return nodeId + ":" + type;
    }

    public static Plan generate(Input in) {
        LearnerGraphSnapshot snap = in.snapshot();
        Settings s = in.settings();
        if (s.deadline().isBefore(in.today())) {
            throw new IllegalArgumentException("截止日期已经过了");
        }
        List<Long> order = order(snap, targets(snap, s));
        Deque<List<Unit>> queue = new ArrayDeque<>();
        for (Long n : order) {
            List<Unit> units = activities(snap, n, s.style()).stream()
                    .filter(u -> !in.done().contains(doneKey(u.nodeId(), u.type())))
                    .toList();
            if (!units.isEmpty()) {
                queue.add(units);
            }
        }
        int nodesNeeded = queue.size();
        int minutesNeeded = queue.stream().flatMap(List::stream).mapToInt(Unit::minutes).sum();

        List<Task> tasks = new ArrayList<>();
        long daysToDeadline = ChronoUnit.DAYS.between(in.today(), s.deadline()) + 1;
        int horizon = (int) Math.min(daysToDeadline, HORIZON_DAYS);
        int capacityToDeadline = 0;
        // 已经学过（掌握度 ≥ 0.5）的节点数，用来判断能不能做交错练习
        int practiced = (int) snap.nodes().keySet().stream().filter(n -> snap.masteryOf(n) >= 0.5).count();
        for (int d = 0; d < daysToDeadline; d++) {
            LocalDate date = in.today().plusDays(d);
            int budget = budget(s, date) - (d == 0 ? in.minutesDoneToday() : 0);
            if (d >= horizon) {
                capacityToDeadline += Math.max(0, budget);
                continue;
            }
            List<Unit> day = new ArrayList<>();
            int cards = in.cardsDue().getOrDefault(date, 0);
            if (cards > 0 && !(d == 0 && in.done().contains(doneKey(null, TaskType.REVIEW_CARDS)))) {
                day.add(new Unit(TaskType.REVIEW_CARDS, null, Math.max(2, (cards + 1) / 2),
                        cards + " 张卡片今天到期，趁快忘的时候复习记得最牢", cards));
            }
            int retests = in.retestsDue().getOrDefault(date, 0);
            if (retests > 0 && !(d == 0 && in.done().contains(doneKey(null, TaskType.RETEST)))) {
                day.add(new Unit(TaskType.RETEST, null, 2 * retests, retests + " 个知识点到了复测时间，看看还记不记得"));
            }
            int used = day.stream().mapToInt(Unit::minutes).sum();
            if (d % 3 == 2 && practiced >= 3 && used + 10 <= budget) {
                day.add(new Unit(TaskType.INTERLEAVED, null, 10, "把学过的几个易混知识点混在一起练，考试时才分得清"));
                used += 10;
            }
            int fixed = used;
            while (!queue.isEmpty()) {
                List<Unit> node = queue.peek();
                int need = node.stream().mapToInt(Unit::minutes).sum();
                // 一个节点的活动排在同一天；当天装不下就挪到明天（除非当天还一个节点都没排）
                if (used + need > budget && (used > fixed || budget - fixed <= 0)) {
                    break;
                }
                queue.poll();
                day.addAll(node);
                used += need;
                practiced++;
            }
            capacityToDeadline += Math.max(0, budget - fixed);
            tasks.addAll(blocks(date, day, s.rhythm()));
        }
        String warning = minutesNeeded > capacityToDeadline ? BEHIND_WARNING : null;
        return new Plan(tasks, warning, nodesNeeded);
    }

    static int budget(Settings s, LocalDate date) {
        DayOfWeek w = date.getDayOfWeek();
        return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY ? s.weekendMinutes() : s.weekdayMinutes();
    }

    /** 范围内没掌握的节点，加上它们（递归）没掌握的前置 */
    static Set<Long> targets(LearnerGraphSnapshot snap, Settings s) {
        Set<Long> seeds = new LinkedHashSet<>();
        for (Long n : snap.nodes().keySet()) {
            if (snap.mastered(n)) {
                continue;
            }
            boolean in = switch (s.scope()) {
                case BOOK -> true;
                case CHAPTERS -> s.chapters().contains(snap.chapterOf().get(n));
                case WEAK -> snap.mastery().containsKey(n);
            };
            if (in) {
                seeds.add(n);
            }
        }
        Set<Long> all = new LinkedHashSet<>();
        Deque<Long> stack = new ArrayDeque<>(seeds);
        while (!stack.isEmpty()) {
            Long n = stack.pop();
            if (all.add(n)) {
                for (Long p : snap.prereqsOf(n)) {
                    if (!snap.mastered(p) && !all.contains(p)) {
                        stack.push(p);
                    }
                }
            }
        }
        return all;
    }

    /** 拓扑序：前置先学；同时能学的节点按“薄弱程度 + 影响面”优先，再按书中顺序 */
    static List<Long> order(LearnerGraphSnapshot snap, Set<Long> targets) {
        Map<Long, Integer> indegree = new HashMap<>();
        Map<Long, List<Long>> next = new HashMap<>();
        for (Long n : targets) {
            indegree.putIfAbsent(n, 0);
            for (Long p : snap.prereqsOf(n)) {
                if (targets.contains(p)) {
                    indegree.merge(n, 1, Integer::sum);
                    next.computeIfAbsent(p, k -> new ArrayList<>()).add(n);
                }
            }
        }
        Map<Long, Integer> impact = new HashMap<>();
        for (Long n : targets) {
            impact.put(n, downstream(n, next).size());
        }
        int maxImpact = Math.max(1, impact.values().stream().mapToInt(Integer::intValue).max().orElse(1));
        Comparator<Long> priority = Comparator.<Long>comparingDouble(
                        n -> -(0.5 * (1 - snap.masteryOf(n)) + 0.5 * impact.get(n) / (double) maxImpact))
                .thenComparing(snap.byOrdinal());
        PriorityQueue<Long> ready = new PriorityQueue<>(priority);
        indegree.forEach((n, d) -> {
            if (d == 0) {
                ready.add(n);
            }
        });
        List<Long> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        while (!ready.isEmpty()) {
            Long n = ready.poll();
            out.add(n);
            seen.add(n);
            for (Long m : next.getOrDefault(n, List.of())) {
                if (indegree.merge(m, -1, Integer::sum) == 0) {
                    ready.add(m);
                }
            }
        }
        // 有环的剩余节点按书中顺序补在后面
        targets.stream().filter(n -> !seen.contains(n)).sorted(snap.byOrdinal()).forEach(out::add);
        return out;
    }

    private static Set<Long> downstream(Long n, Map<Long, List<Long>> next) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>(next.getOrDefault(n, List.of()));
        while (!stack.isEmpty()) {
            Long m = stack.pop();
            if (seen.add(m)) {
                stack.addAll(next.getOrDefault(m, List.of()));
            }
        }
        return seen;
    }

    /** 节点类型 × 主风格 → 活动 */
    static List<Unit> activities(LearnerGraphSnapshot snap, Long n, MainStyle style) {
        NodeType type = snap.type(n);
        boolean fresh = snap.masteryOf(n) < 0.3;
        boolean testable = snap.testable(n);
        List<Unit> units = new ArrayList<>();
        switch (type) {
            case FACT -> {
                if (fresh) {
                    units.add(new Unit(TaskType.NEW_LEARN, n, 5, "事实类知识点：先读一遍，卡片会按遗忘曲线提醒你复习"));
                }
                if (testable) {
                    units.add(new Unit(TaskType.QUIZ, n, style == MainStyle.MEMORIZE ? 5 : 3, "马上做几道题主动回忆，比重读记得牢"));
                }
            }
            case CONCEPT, PRINCIPLE -> {
                if (fresh || style == MainStyle.MEMORIZE) {
                    units.add(new Unit(TaskType.NEW_LEARN, n, 5, "先读懂定义和例子"));
                }
                if (style != MainStyle.MEMORIZE || !testable) {
                    units.add(new Unit(TaskType.FEYNMAN, n, 10,
                            (type == NodeType.PRINCIPLE ? "原理" : "概念") + "要真懂：用自己的话讲给别人听，讲不清的地方就是没懂"));
                }
                if (testable) {
                    units.add(new Unit(TaskType.QUIZ, n, 5, "用题目检验是不是真的理解了"));
                }
            }
            case METHOD -> {
                if (fresh) {
                    units.add(new Unit(TaskType.NEW_LEARN, n, 5, "先看懂方法的步骤"));
                }
                if (style == MainStyle.UNDERSTAND) {
                    units.add(new Unit(TaskType.FEYNMAN, n, 10, "讲清楚每一步为什么这样做"));
                }
                if (testable) {
                    units.add(new Unit(TaskType.DELIBERATE, n, style == MainStyle.PRACTICE ? 15 : 10,
                            "方法要靠练：先验证卡在哪一步，再针对性地练"));
                } else {
                    units.add(new Unit(TaskType.FEYNMAN, n, 10, "这个方法还没有题目，先把步骤讲清楚"));
                }
            }
            case SYNTHESIS -> {
                if (testable) {
                    units.add(new Unit(TaskType.FIRST_PRINCIPLES, n, 20, "综合题：从目标倒推需要哪些知识点，哪里不会补哪里"));
                } else {
                    units.add(new Unit(TaskType.FEYNMAN, n, 10, "把这几个知识点怎么组合起来讲清楚"));
                }
            }
        }
        // 同一节点同类活动只保留一个
        Set<TaskType> seen = new HashSet<>();
        return units.stream().filter(u -> seen.add(u.type())).toList();
    }

    /** 按节奏切块：装满一块就开下一块；比一块还长的任务单独占一块 */
    static List<Task> blocks(LocalDate date, List<Unit> day, Rhythm rhythm) {
        List<Task> out = new ArrayList<>();
        int block = 1;
        int fill = 0;
        int ordinal = 0;
        for (Unit u : day) {
            if (fill > 0 && fill + u.minutes() > rhythm.blockMinutes) {
                block++;
                fill = 0;
                ordinal = 0;
            }
            out.add(new Task(date, block, ordinal++, u.type(), u.nodeId(), u.cardCount(), u.minutes(), u.reason()));
            fill += u.minutes();
        }
        return out;
    }
}
