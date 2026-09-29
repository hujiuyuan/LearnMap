package com.learnmap.graph;

import java.util.List;

/** 学习者副本里新加了节点（例如合并了新版图谱），用于给这些节点补复习卡片 */
public record LearnerNodesAdded(Long graphId, Long learnerId, List<Long> nodeIds) {
}
