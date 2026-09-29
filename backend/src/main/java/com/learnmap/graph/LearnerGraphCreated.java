package com.learnmap.graph;

/** 学习者拿到一份新的图谱副本（同一事务内同步发布），复习卡片等据此初始化。 */
public record LearnerGraphCreated(Long graphId, Long learnerId) {
}
