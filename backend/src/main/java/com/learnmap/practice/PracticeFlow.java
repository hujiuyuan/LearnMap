package com.learnmap.practice;

import java.util.List;

/**
 * 一种学习方式的出题流程。流程是纯逻辑：看快照和已答的题，决定下一批考哪些节点、什么时候结束。
 * 存储、判分、写证据由 {@link PracticeService} 统一处理。
 */
interface PracticeFlow<S> {

    PracticeMode mode();

    Class<S> stateType();

    /** 开始：返回第一批 */
    Decision<S> begin(LearnerGraphSnapshot snap, Long targetNodeId, long seed);

    /** 当前这一批都答完了：返回下一批，或者结束 */
    Decision<S> next(LearnerGraphSnapshot snap, Long targetNodeId, S state, PracticePhase phase,
                     List<PracticeItem> batch, List<PracticeItem> all);

    /** 当前批次需要展示的讲解（只有第一性原理的补缺阶段有） */
    default Long lessonNode(S state, PracticePhase phase) {
        return null;
    }

    /**
     * @param nodes      下一批每道题考的节点（同一个节点可以出现多次）
     * @param learned    这一步学会的节点，安排 3 / 7 / 21 天延时复测
     * @param recheck    需要 3 天后复验的节点（刻意练习）
     * @param summary    结束时给学习者看的总结
     */
    record Decision<S>(PracticePhase phase, List<Long> nodes, S state, boolean done, String summary,
                       List<Long> learned, List<Long> recheck) {

        static <S> Decision<S> batch(PracticePhase phase, List<Long> nodes, S state) {
            return new Decision<>(phase, nodes, state, false, null, List.of(), List.of());
        }

        static <S> Decision<S> finish(S state, String summary, List<Long> learned, List<Long> recheck) {
            return new Decision<>(PracticePhase.DONE, List.of(), state, true, summary, learned, recheck);
        }

        Decision<S> withLearned(List<Long> nodes) {
            return new Decision<>(phase, this.nodes, state, done, summary, nodes, recheck);
        }
    }

    static long correctCount(List<PracticeItem> items) {
        return items.stream().filter(i -> Boolean.TRUE.equals(i.getCorrect())).count();
    }
}
