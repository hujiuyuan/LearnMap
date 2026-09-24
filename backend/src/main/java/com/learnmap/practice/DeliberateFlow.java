package com.learnmap.practice;

import com.learnmap.evidence.ErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 刻意练习（V1 的“诊断修复”）：针对根因节点先出 3 道验证题；确实不会就做 5 道专项练习
 * （根因本身 3 道 + 掌握度低的直接前置最多 2 道），3 天后复验。
 */
@Component
class DeliberateFlow implements PracticeFlow<DeliberateFlow.State> {

    static final int VERIFY_COUNT = 3;
    static final int PRACTICE_ON_TARGET = 3;
    static final int PRACTICE_ON_PREREQS = 2;

    static class State {
        /** 刻意练习不需要额外状态，保留一个字段便于序列化 */
        public int version = 1;
    }

    @Override
    public PracticeMode mode() {
        return PracticeMode.DELIBERATE;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public Decision<State> begin(LearnerGraphSnapshot snap, Long target, long seed) {
        if (target == null || !snap.testable(target)) {
            throw new IllegalArgumentException("刻意练习需要指定一个有题目的知识点");
        }
        return Decision.batch(PracticePhase.VERIFY, Collections.nCopies(VERIFY_COUNT, target), new State());
    }

    @Override
    public Decision<State> next(LearnerGraphSnapshot snap, Long target, State state, PracticePhase phase,
                                List<PracticeItem> batch, List<PracticeItem> all) {
        String name = snap.name(target);
        if (phase == PracticePhase.VERIFY) {
            long correct = PracticeFlow.correctCount(batch);
            if (correct == VERIFY_COUNT) {
                return Decision.finish(state, "验证题 " + VERIFY_COUNT + " 道全对，「" + name + "」可能已经掌握，暂时不需要专项练习。",
                        List.of(), List.of());
            }
            List<Long> nodes = new ArrayList<>(Collections.nCopies(PRACTICE_ON_TARGET, target));
            snap.prereqsOf(target).stream()
                    .filter(p -> snap.testable(p) && snap.masteryOf(p) < 0.5)
                    .limit(PRACTICE_ON_PREREQS)
                    .forEach(nodes::add);
            return Decision.batch(PracticePhase.PRACTICE, nodes, state);
        }
        long correct = PracticeFlow.correctCount(batch);
        long careless = batch.stream().filter(i -> i.getErrorType() == ErrorType.CARELESS || i.getErrorType() == ErrorType.CALCULATION).count();
        String summary = "专项练习做对 " + correct + " / " + batch.size() + " 道"
                + (careless > 0 ? "，其中 " + careless + " 道是计算或粗心错误" : "")
                + "。3 天后会再出题复验「" + name + "」。";
        return Decision.finish(state, summary, List.of(), List.of(target));
    }
}
