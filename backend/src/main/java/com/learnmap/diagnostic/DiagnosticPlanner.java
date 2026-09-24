package com.learnmap.diagnostic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入门诊断的选题规则（沿用 V1 “知识体检”三轮），纯函数，便于测试：
 * <ol>
 *   <li>广度扫描：在全书范围内按出现顺序均匀抽节点，每个节点 1 题；</li>
 *   <li>定向诊断：从做错的节点沿前置关系往下找（最多 3 层），测还没测过的前置；</li>
 *   <li>验证：对“疑似根因”（做错了、但它的前置都没错）再出一题确认。</li>
 * </ol>
 * 前置关系用 {@code prereqs}：节点 → 它的直接前置节点。
 */
public final class DiagnosticPlanner {

    public static final int BREADTH_MAX = 20;
    public static final int TARGETED_MAX = 15;
    public static final int VERIFY_MAX = 10;
    static final int TARGETED_DEPTH = 3;

    private DiagnosticPlanner() {
    }

    /** 按书内顺序均匀抽样 */
    public static List<Long> breadth(List<Long> orderedTestable, int max) {
        int n = orderedTestable.size();
        if (n <= max) {
            return List.copyOf(orderedTestable);
        }
        List<Long> picked = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            picked.add(orderedTestable.get((int) Math.floor((double) i * n / max)));
        }
        return picked;
    }

    /** 从做错的节点出发，按离错题的层数由近到远，找还没测过、有题可出的前置 */
    public static List<Long> targeted(Collection<Long> wrong, Map<Long, List<Long>> prereqs, Set<Long> tested,
                                      Set<Long> testable, int max) {
        LinkedHashSet<Long> picked = new LinkedHashSet<>();
        Set<Long> seen = new HashSet<>(wrong);
        Deque<long[]> queue = new ArrayDeque<>();
        wrong.forEach(w -> queue.add(new long[]{w, 0}));
        while (!queue.isEmpty() && picked.size() < max) {
            long[] cur = queue.poll();
            if (cur[1] >= TARGETED_DEPTH) {
                continue;
            }
            for (Long p : prereqs.getOrDefault(cur[0], List.of())) {
                if (!seen.add(p)) {
                    continue; // 共享前置只查一次；遇到环也会在这里停下
                }
                if (!tested.contains(p) && testable.contains(p)) {
                    picked.add(p);
                    if (picked.size() >= max) {
                        break;
                    }
                }
                queue.add(new long[]{p, cur[1] + 1});
            }
        }
        return List.copyOf(picked);
    }

    /** 疑似根因：做错了，并且测过的直接前置里没有做错的；按影响的后续节点数从多到少排 */
    public static List<Long> suspects(Map<Long, Boolean> results, Map<Long, List<Long>> prereqs, int max) {
        Map<Long, List<Long>> dependents = invert(prereqs);
        return results.entrySet().stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .filter(id -> prereqs.getOrDefault(id, List.of()).stream().noneMatch(p -> Boolean.FALSE.equals(results.get(p))))
                .sorted(Comparator.comparingInt((Long id) -> -impact(id, dependents)).thenComparing(id -> id))
                .limit(max)
                .toList();
    }

    /** 图谱影响：这个节点之后有多少个节点直接或间接依赖它 */
    public static int impact(Long nodeId, Map<Long, List<Long>> dependents) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>(dependents.getOrDefault(nodeId, List.of()));
        while (!stack.isEmpty()) {
            Long d = stack.pop();
            if (!d.equals(nodeId) && seen.add(d)) {
                stack.addAll(dependents.getOrDefault(d, List.of()));
            }
        }
        return seen.size();
    }

    public static Map<Long, List<Long>> invert(Map<Long, List<Long>> prereqs) {
        Map<Long, List<Long>> out = new HashMap<>();
        prereqs.forEach((node, ps) -> ps.forEach(p -> out.computeIfAbsent(p, k -> new ArrayList<>()).add(node)));
        return out;
    }
}
