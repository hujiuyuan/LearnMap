package com.learnmap.practice;

import com.learnmap.evidence.ErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 交错练习（产品方案 3.3）：至少 3 个已学过（掌握度 ≥ 50%）的节点才开始；
 * 优先混易混淆（CONTRASTS）的节点，其次同一章同类型、同一章；题目打乱顺序，不提示考哪个知识点。
 * 除了对错，还统计“方法选错”的次数。
 */
@Component
class InterleavedFlow implements PracticeFlow<InterleavedFlow.State> {

    static final double ELIGIBLE_MASTERY = 0.5;
    static final int MIN_NODES = 3;
    static final int MAX_NODES = 4;
    static final int QUESTIONS = 8;

    static class State {
        public List<Long> group = new ArrayList<>();
    }

    @Override
    public PracticeMode mode() {
        return PracticeMode.INTERLEAVED;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public Decision<State> begin(LearnerGraphSnapshot snap, Long target, long seed) {
        List<Long> group = chooseGroup(snap, target);
        State state = new State();
        state.group = group;
        List<Long> nodes = new ArrayList<>();
        for (int i = 0; i < QUESTIONS; i++) {
            nodes.add(group.get(i % group.size()));
        }
        Collections.shuffle(nodes, new Random(seed));
        return Decision.batch(PracticePhase.MIXED, nodes, state);
    }

    static List<Long> chooseGroup(LearnerGraphSnapshot snap, Long target) {
        List<Long> eligible = snap.nodes().keySet().stream()
                .filter(id -> snap.testable(id) && snap.masteryOf(id) >= ELIGIBLE_MASTERY)
                .toList();
        if (eligible.size() < MIN_NODES) {
            throw new IllegalStateException("至少要有 " + MIN_NODES + " 个学过（掌握度 ≥ 50%）的知识点才能开始交错练习，现在有 "
                    + eligible.size() + " 个");
        }
        Long seedNode = target != null && eligible.contains(target) ? target : eligible.stream()
                .sorted(Comparator.comparingInt((Long id) -> -snap.contrasts().getOrDefault(id, Set.of()).size())
                        .thenComparingDouble(snap::masteryOf)
                        .thenComparing(snap.byOrdinal()))
                .findFirst().orElseThrow();
        LinkedHashSet<Long> group = new LinkedHashSet<>();
        group.add(seedNode);
        Long chapter = snap.chapterOf().get(seedNode);
        eligible.stream().filter(id -> snap.contrasts().getOrDefault(seedNode, Set.of()).contains(id)).forEach(group::add);
        eligible.stream().filter(id -> chapter.equals(snap.chapterOf().get(id)) && snap.type(id) == snap.type(seedNode))
                .sorted(snap.byOrdinal()).forEach(group::add);
        eligible.stream().filter(id -> chapter.equals(snap.chapterOf().get(id))).sorted(snap.byOrdinal()).forEach(group::add);
        eligible.stream().sorted(snap.byOrdinal()).forEach(group::add);
        return group.stream().limit(MAX_NODES).toList();
    }

    @Override
    public Decision<State> next(LearnerGraphSnapshot snap, Long target, State state, PracticePhase phase,
                                List<PracticeItem> batch, List<PracticeItem> all) {
        long correct = PracticeFlow.correctCount(batch);
        long wrongMethod = batch.stream().filter(i -> i.getErrorType() == ErrorType.WRONG_METHOD).count();
        String names = String.join("、", state.group.stream().map(snap::name).toList());
        String summary = "混合练习（" + names + "）做对 " + correct + " / " + batch.size() + " 道"
                + (wrongMethod > 0 ? "，有 " + wrongMethod + " 道是方法选错：看到题先想想该用哪个知识点。" : "。");
        return Decision.finish(state, summary, List.of(), List.of());
    }
}
