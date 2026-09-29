package com.learnmap.diagnostic;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticPlannerTest {

    // 完全平方公式(1) → 配方法(2) → 一元二次方程(3) → 二次函数顶点(4) → 二次函数最值(5)
    static final Map<Long, List<Long>> CHAIN = Map.of(2L, List.of(1L), 3L, List.of(2L), 4L, List.of(3L), 5L, List.of(4L));

    @Test
    void breadthSamplesEvenlyAcrossTheBook() {
        List<Long> all = LongStream.rangeClosed(1, 100).boxed().toList();
        List<Long> picked = DiagnosticPlanner.breadth(all, 20);
        assertThat(picked).hasSize(20).startsWith(1L).contains(51L).doesNotHaveDuplicates();
        assertThat(DiagnosticPlanner.breadth(List.of(1L, 2L), 20)).containsExactly(1L, 2L);
    }

    @Test
    void targetedWalksPrerequisitesNearestFirstAndSkipsTested() {
        List<Long> picked = DiagnosticPlanner.targeted(List.of(5L), CHAIN, Set.of(5L, 3L), Set.of(1L, 2L, 3L, 4L, 5L), 15);
        assertThat(picked).containsExactly(4L, 2L); // 3 已测过；深度上限 3 层，到 2 为止
    }

    @Test
    void targetedVisitsSharedPrerequisiteOnceAndSurvivesCycles() {
        Map<Long, List<Long>> shared = Map.of(10L, List.of(11L, 12L), 11L, List.of(13L), 12L, List.of(13L), 13L, List.of(10L));
        List<Long> picked = DiagnosticPlanner.targeted(List.of(10L), shared, Set.of(10L), Set.of(10L, 11L, 12L, 13L), 15);
        assertThat(picked).containsExactly(11L, 12L, 13L);
    }

    @Test
    void suspectIsTheWrongNodeWhosePrerequisitesAreFine() {
        Map<Long, Boolean> results = new LinkedHashMap<>();
        results.put(1L, true);   // 完全平方公式 会
        results.put(2L, false);  // 配方法 不会
        results.put(3L, false);  // 一元二次方程 不会（受配方法影响）
        results.put(5L, false);  // 最值 不会，但前置 4 没测
        List<Long> suspects = DiagnosticPlanner.suspects(results, CHAIN, 10);
        assertThat(suspects).containsExactly(2L, 5L); // 配方法影响 3 个后续节点，排在前面
    }

    @Test
    void impactCountsAllDownstreamNodes() {
        Map<Long, List<Long>> dependents = DiagnosticPlanner.invert(CHAIN);
        assertThat(DiagnosticPlanner.impact(1L, dependents)).isEqualTo(4);
        assertThat(DiagnosticPlanner.impact(5L, dependents)).isZero();
    }
}
