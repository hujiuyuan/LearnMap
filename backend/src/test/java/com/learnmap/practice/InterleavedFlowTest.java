package com.learnmap.practice;

import com.learnmap.graph.NodeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterleavedFlowTest {

    @Test
    void needsThreeLearnedNodes() {
        Snapshots s = new Snapshots().node(1, NodeType.METHOD, 1).node(2, NodeType.METHOD, 1).node(3, NodeType.METHOD, 1)
                .mastery(1, 0.6, 0.5).mastery(2, 0.6, 0.5).mastery(3, 0.3, 0.5);
        assertThatThrownBy(() -> InterleavedFlow.chooseGroup(s.build(), null)).hasMessageContaining("至少要有 3 个");
    }

    @Test
    void prefersContrastingNodesThenSameChapterAndType() {
        Snapshots s = new Snapshots()
                .node(1, NodeType.METHOD, 1).node(2, NodeType.METHOD, 1).node(3, NodeType.CONCEPT, 1)
                .node(4, NodeType.METHOD, 2).node(5, NodeType.METHOD, 1).node(6, NodeType.METHOD, 3)
                .contrast(5, 4).contrast(5, 1);
        for (long id = 1; id <= 6; id++) {
            s.mastery(id, 0.7, 0.5);
        }
        // 5 的易混淆关系最多，作为种子；先加易混淆的 1、4（跨章也要），再加同章同类型的 2，概念类的 3 和别章的 6 不进组
        assertThat(InterleavedFlow.chooseGroup(s.build(), null)).containsExactly(5L, 1L, 4L, 2L);
    }

    @Test
    void mixesEightQuestionsAcrossTheGroupInShuffledOrder() {
        Snapshots s = new Snapshots().node(1, NodeType.METHOD, 1).node(2, NodeType.METHOD, 1).node(3, NodeType.METHOD, 1);
        for (long id = 1; id <= 3; id++) {
            s.mastery(id, 0.7, 0.5);
        }
        var d = new InterleavedFlow().begin(s.build(), null, 42);
        assertThat(d.phase()).isEqualTo(PracticePhase.MIXED);
        assertThat(d.nodes()).hasSize(8).containsOnly(1L, 2L, 3L);
        assertThat(d.nodes()).isNotEqualTo(java.util.List.of(1L, 2L, 3L, 1L, 2L, 3L, 1L, 2L));
    }
}
