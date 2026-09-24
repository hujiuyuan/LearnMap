package com.learnmap.graph;

import com.learnmap.graph.GraphDiff.DiffNode;
import com.learnmap.graph.GraphDiff.DiffRelation;
import com.learnmap.graph.GraphDiff.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphDiffTest {

    @Test
    void alignsNodesByNameAndReportsAddedRemovedChanged() {
        List<DiffNode> before = List.of(
                new DiffNode(1L, "等式的性质", "两边同加同一个数", NodeType.PRINCIPLE, 10L),
                new DiffNode(2L, "移项", "从一边移到另一边要变号", NodeType.METHOD, 10L),
                new DiffNode(3L, "旧知识点", "x", NodeType.FACT, 10L));
        List<DiffNode> after = List.of(
                new DiffNode(11L, "等式的性质 ", "两边同加同一个数", NodeType.PRINCIPLE, 10L),
                new DiffNode(12L, "移项", "移项要改变符号", NodeType.CONCEPT, 10L),
                new DiffNode(14L, "检验", "代回原方程", NodeType.METHOD, 10L));
        Result d = GraphDiff.compute(before,
                List.of(new DiffRelation("等式的性质", "移项", RelationType.PREREQUISITE),
                        new DiffRelation("移项", "旧知识点", RelationType.PREREQUISITE)),
                after,
                List.of(new DiffRelation("等式的性质", "移项", RelationType.PREREQUISITE),
                        new DiffRelation("移项", "检验", RelationType.PREREQUISITE)));

        assertThat(d.matched()).isEqualTo(Map.of(1L, 11L, 2L, 12L));
        assertThat(d.added()).extracting(DiffNode::name).containsExactly("检验");
        assertThat(d.removed()).extracting(DiffNode::name).containsExactly("旧知识点");
        assertThat(d.changed()).singleElement().satisfies(c -> {
            assertThat(c.before().name()).isEqualTo("移项");
            assertThat(c.fields()).containsExactly("定义", "类型");
        });
        assertThat(d.addedRelations()).containsExactly(new DiffRelation("移项", "检验", RelationType.PREREQUISITE));
        assertThat(d.removedRelations()).containsExactly(new DiffRelation("移项", "旧知识点", RelationType.PREREQUISITE));
        assertThat(d.isEmpty()).isFalse();
    }

    @Test
    void identicalGraphsHaveNoDifference() {
        List<DiffNode> nodes = List.of(new DiffNode(1L, "A", "a", NodeType.FACT, null));
        assertThat(GraphDiff.compute(nodes, List.of(), List.of(new DiffNode(9L, "a", "a", NodeType.FACT, null)), List.of()).isEmpty()).isTrue();
    }
}
