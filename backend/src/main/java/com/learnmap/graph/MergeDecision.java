package com.learnmap.graph;

public enum MergeDecision {
    /** 合并：加上新版多出来的节点和关系，旧的都保留 */
    MERGE,
    /** 保留旧版：什么都不变 */
    KEEP,
    /** 换成新版：按新版更新，只保留有学习记录的旧节点 */
    REPLACE
}
