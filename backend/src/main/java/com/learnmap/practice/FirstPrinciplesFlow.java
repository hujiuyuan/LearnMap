package com.learnmap.practice;

import com.learnmap.mastery.MasteryEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 第一性原理：目标拆解 + 递归补缺（产品方案 3.4）。
 * <pre>
 * 补缺(X):
 *   X 已掌握 → 跳过
 *   X 数据不足 → 先出 2 道探测题，都对算会
 *   X 不会 → 先检查 X 的前置（递归），再讲解 X + 3 道练习/验证；没通过换一轮，还不行标记给辅导者
 * 最后回到目标本身，做 2 道题。
 * </pre>
 * 保护：共享前置只查一次；递归最多 4 层；要补的超过 15 个提示“离基础较远”；遇到环（已检查过的节点）就停。
 */
@Component
class FirstPrinciplesFlow implements PracticeFlow<FirstPrinciplesFlow.State> {

    static final int MAX_DEPTH = 4;
    static final int MAX_GAPS = 15;
    static final int PROBES_PER_NODE = 2;
    static final int LEARN_ITEMS = 3;
    static final int LEARN_PASS = 2;
    static final int MAX_ATTEMPTS = 2;
    static final int GOAL_ITEMS = 2;

    public static class State {
        /** 节点 → 离目标几层（目标是 0） */
        public Map<Long, Integer> depth = new HashMap<>();
        public List<Long> known = new ArrayList<>();
        public List<Long> gaps = new ArrayList<>();
        public List<Long> noQuestions = new ArrayList<>();
        public List<Long> frontier = new ArrayList<>();
        public List<Long> learnOrder = new ArrayList<>();
        public int learnIndex;
        public int attempts;
        public List<Long> passed = new ArrayList<>();
        public List<Long> flagged = new ArrayList<>();
        public boolean tooFar;
    }

    @Override
    public PracticeMode mode() {
        return PracticeMode.FIRST_PRINCIPLES;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public Decision<State> begin(LearnerGraphSnapshot snap, Long target, long seed) {
        if (target == null || !snap.nodes().containsKey(target)) {
            throw new IllegalArgumentException("请先选一个目标知识点");
        }
        State s = new State();
        s.depth.put(target, 0);
        s.frontier.add(target);
        return expand(snap, target, s);
    }

    @Override
    public Decision<State> next(LearnerGraphSnapshot snap, Long target, State s, PracticePhase phase,
                                List<PracticeItem> batch, List<PracticeItem> all) {
        switch (phase) {
            case PROBE -> {
                Map<Long, Integer> correct = new HashMap<>();
                batch.forEach(i -> correct.merge(i.getNodeId(), Boolean.TRUE.equals(i.getCorrect()) ? 1 : 0, Integer::sum));
                for (Long id : new LinkedHashSet<>(batch.stream().map(PracticeItem::getNodeId).toList())) {
                    if (correct.getOrDefault(id, 0) >= PROBES_PER_NODE) {
                        s.known.add(id);
                    } else {
                        addGap(snap, s, id);
                    }
                }
                return expand(snap, target, s);
            }
            case LEARN -> {
                Long current = s.learnOrder.get(s.learnIndex);
                List<Long> learned = List.of();
                if (PracticeFlow.correctCount(batch) >= LEARN_PASS) {
                    s.passed.add(current);
                    learned = List.of(current);
                    s.learnIndex++;
                    s.attempts = 0;
                } else if (++s.attempts >= MAX_ATTEMPTS) {
                    s.flagged.add(current);
                    s.learnIndex++;
                    s.attempts = 0;
                }
                return learnOrGoal(snap, target, s).withLearned(learned);
            }
            case GOAL -> {
                return Decision.finish(s, summary(snap, target, s, PracticeFlow.correctCount(batch)), List.of(), List.of());
            }
            default -> throw new IllegalStateException("第一性原理没有这个阶段：" + phase);
        }
    }

    @Override
    public Long lessonNode(State s, PracticePhase phase) {
        return phase == PracticePhase.LEARN && s.learnIndex < s.learnOrder.size() ? s.learnOrder.get(s.learnIndex) : null;
    }

    /** 处理 frontier：会的跳过、确定不会的继续往前置拆，数据不足的收集起来出探测题 */
    private Decision<State> expand(LearnerGraphSnapshot snap, Long target, State s) {
        List<Long> probe = new ArrayList<>();
        while (!s.frontier.isEmpty()) {
            Long x = s.frontier.remove(0);
            if (snap.mastered(x)) {
                s.known.add(x);
            } else if (snap.confidenceOf(x) >= MasteryEngine.CONFIDENT && snap.masteryOf(x) < 0.5) {
                addGap(snap, s, x);
            } else if (!snap.testable(x)) {
                s.noQuestions.add(x); // 没题可测也没法验证，只能跳过
            } else {
                probe.add(x);
            }
        }
        if (!probe.isEmpty()) {
            List<Long> nodes = new ArrayList<>();
            probe.forEach(p -> nodes.addAll(Collections.nCopies(PROBES_PER_NODE, p)));
            return Decision.batch(PracticePhase.PROBE, nodes, s);
        }
        // 拆到底了：从最深的一层往上补
        s.learnOrder = s.gaps.stream()
                .filter(snap::testable)
                .sorted(Comparator.comparingInt((Long id) -> -s.depth.get(id)).thenComparing(snap.byOrdinal()))
                .toList();
        s.learnIndex = 0;
        return learnOrGoal(snap, target, s);
    }

    private void addGap(LearnerGraphSnapshot snap, State s, Long x) {
        if (s.gaps.contains(x)) {
            return;
        }
        s.gaps.add(x);
        if (s.gaps.size() > MAX_GAPS) {
            s.tooFar = true;
            return;
        }
        int d = s.depth.get(x);
        for (Long p : snap.prereqsOf(x)) {
            if (s.depth.containsKey(p)) {
                continue; // 共享前置只查一次；环也在这里停下
            }
            if (d + 1 > MAX_DEPTH) {
                s.tooFar = true;
                continue;
            }
            s.depth.put(p, d + 1);
            s.frontier.add(p);
        }
    }

    private Decision<State> learnOrGoal(LearnerGraphSnapshot snap, Long target, State s) {
        if (s.learnIndex < s.learnOrder.size()) {
            Long node = s.learnOrder.get(s.learnIndex);
            return Decision.batch(PracticePhase.LEARN, Collections.nCopies(LEARN_ITEMS, node), s);
        }
        if (!snap.testable(target)) {
            return Decision.finish(s, summary(snap, target, s, -1), List.of(), List.of());
        }
        return Decision.batch(PracticePhase.GOAL, Collections.nCopies(GOAL_ITEMS, target), s);
    }

    private static String summary(LearnerGraphSnapshot snap, Long target, State s, long goalCorrect) {
        StringBuilder sb = new StringBuilder("目标「").append(snap.name(target)).append("」");
        if (goalCorrect >= 0) {
            sb.append(goalCorrect == GOAL_ITEMS ? "：最后的目标题全部做对。" : "：最后的目标题做对 " + goalCorrect + " / " + GOAL_ITEMS + " 道。");
        } else {
            sb.append("：这个知识点还没有题目，没法做最后的检验。");
        }
        int deepest = s.gaps.stream().mapToInt(s.depth::get).max().orElse(0);
        if (s.passed.isEmpty() && s.flagged.isEmpty()) {
            sb.append("检查下来前置知识都会，不需要补。");
        } else {
            sb.append("往下补了 ").append(s.passed.size()).append(" 个知识点，最深 ").append(deepest).append(" 层。");
        }
        if (!s.flagged.isEmpty()) {
            sb.append("「").append(String.join("、", s.flagged.stream().map(snap::name).toList()))
                    .append("」两轮都没通过，已经标记给辅导者。");
        }
        if (s.tooFar) {
            sb.append("这个目标离你现在的基础比较远，建议先设一个中间目标。");
        }
        return sb.toString();
    }
}
