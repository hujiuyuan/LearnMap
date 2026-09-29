package com.learnmap.practice;

import com.learnmap.graph.NodeType;
import com.learnmap.practice.FirstPrinciplesFlow.State;
import com.learnmap.practice.PracticeFlow.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirstPrinciplesFlowTest {

    final FirstPrinciplesFlow flow = new FirstPrinciplesFlow();

    /** 目标 10 需要 A(1) B(2)；B 需要 E(5)；C(3) 也需要 E —— E 只查一次 */
    Snapshots graph() {
        Snapshots s = new Snapshots();
        for (long id : new long[]{1, 2, 3, 5, 10}) {
            s.node(id, NodeType.METHOD, 1);
        }
        return s.prereq(1, 10).prereq(2, 10).prereq(3, 10).prereq(5, 2).prereq(5, 3);
    }

    @Test
    void probesUnknownThenRecursesIntoGapsAndLearnsBottomUp() {
        LearnerGraphSnapshot snap = graph()
                .mastery(1, 0.9, 0.9)        // A 已掌握：跳过
                .mastery(3, 0.2, 0.8)        // C 确定不会：不用探测，直接往下拆
                .build();
        // 目标本身数据不足 → 先探测目标
        Decision<State> d = flow.begin(snap, 10L, 1);
        assertThat(d.phase()).isEqualTo(PracticePhase.PROBE);
        assertThat(d.nodes()).containsExactly(10L, 10L);

        // 目标做错 → 拆到 A B C：A 会、C 确定不会（拆出 E）、B 数据不足要探测；E 数据不足也要探测
        d = flow.next(snap, 10L, d.state(), PracticePhase.PROBE, Snapshots.answered(d.nodes(), false), List.of());
        assertThat(d.phase()).isEqualTo(PracticePhase.PROBE);
        assertThat(d.nodes()).containsExactlyInAnyOrder(2L, 2L, 5L, 5L);

        // B 会，E 不会 → E 没有前置了，开始从最深处往上补：E（第 2 层）→ C（第 1 层）→ 目标（第 0 层）
        List<Long> probe = d.nodes();
        boolean[] results = new boolean[probe.size()];
        for (int i = 0; i < probe.size(); i++) {
            results[i] = probe.get(i) == 2L;
        }
        d = flow.next(snap, 10L, d.state(), PracticePhase.PROBE, Snapshots.answered(probe, results), List.of());
        assertThat(d.phase()).isEqualTo(PracticePhase.LEARN);
        assertThat(d.state().learnOrder).containsExactly(5L, 3L, 10L);
        assertThat(flow.lessonNode(d.state(), d.phase())).isEqualTo(5L);

        // E 第一轮没过 → 同一个节点再来一轮；第二轮过了 → 进入 C，并安排 E 的延时复测
        d = flow.next(snap, 10L, d.state(), PracticePhase.LEARN, Snapshots.answered(d.nodes(), false), List.of());
        assertThat(d.nodes()).containsOnly(5L);
        d = flow.next(snap, 10L, d.state(), PracticePhase.LEARN, Snapshots.answered(d.nodes(), true), List.of());
        assertThat(d.learned()).containsExactly(5L);
        assertThat(d.nodes()).containsOnly(3L);

        // C 两轮都没过 → 标记给辅导者，继续补目标
        d = flow.next(snap, 10L, d.state(), PracticePhase.LEARN, Snapshots.answered(d.nodes(), false), List.of());
        d = flow.next(snap, 10L, d.state(), PracticePhase.LEARN, Snapshots.answered(d.nodes(), false), List.of());
        assertThat(d.state().flagged).containsExactly(3L);
        assertThat(d.nodes()).containsOnly(10L);
        d = flow.next(snap, 10L, d.state(), PracticePhase.LEARN, Snapshots.answered(d.nodes(), true), List.of());

        // 最后回到目标题
        assertThat(d.phase()).isEqualTo(PracticePhase.GOAL);
        d = flow.next(snap, 10L, d.state(), PracticePhase.GOAL, Snapshots.answered(d.nodes(), true), List.of());
        assertThat(d.done()).isTrue();
        assertThat(d.summary()).contains("全部做对", "补了 2 个知识点", "最深 2 层", "N3", "标记给辅导者");
    }

    @Test
    void masteredTargetGoesStraightToGoal() {
        LearnerGraphSnapshot snap = graph().mastery(10, 0.95, 0.9).build();
        Decision<State> d = flow.begin(snap, 10L, 1);
        assertThat(d.phase()).isEqualTo(PracticePhase.GOAL);
    }

    @Test
    void cycleInPrerequisitesStops() {
        Snapshots s = new Snapshots().node(1, NodeType.CONCEPT, 1).node(2, NodeType.CONCEPT, 1)
                .prereq(1, 2).prereq(2, 1)
                .mastery(1, 0.1, 0.9).mastery(2, 0.1, 0.9);
        Decision<State> d = flow.begin(s.build(), 2L, 1);
        assertThat(d.phase()).isEqualTo(PracticePhase.LEARN);
        assertThat(d.state().learnOrder).containsExactly(1L, 2L);
    }

    @Test
    void deepChainsAreCappedAndFlaggedAsTooFar() {
        Snapshots s = new Snapshots();
        for (long id = 1; id <= 7; id++) {
            s.node(id, NodeType.CONCEPT, 1).mastery(id, 0.1, 0.9);
            if (id > 1) {
                s.prereq(id - 1, id);
            }
        }
        Decision<State> d = flow.begin(s.build(), 7L, 1);
        assertThat(d.state().learnOrder).hasSize(5); // 目标 + 往下 4 层
        assertThat(d.state().tooFar).isTrue();
    }
}
