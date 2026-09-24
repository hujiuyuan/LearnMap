package com.learnmap.graph;

/** 节点类型决定计划里给这个节点配哪种学习活动。 */
public enum NodeType {
    /** 事实/术语 → 间隔重复 + 主动回忆 */
    FACT,
    /** 概念 → 费曼讲解 */
    CONCEPT,
    /** 原理/定理/公式 → 先费曼理解，再间隔重复 */
    PRINCIPLE,
    /** 方法/技能 → 刻意练习 + 交错练习 */
    METHOD,
    /** 综合应用 → 问题驱动 / 刻意练习 */
    SYNTHESIS
}
