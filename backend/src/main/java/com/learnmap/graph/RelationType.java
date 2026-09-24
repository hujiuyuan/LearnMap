package com.learnmap.graph;

public enum RelationType {
    /** from 是 to 的前置：先会 from 才能学 to */
    PREREQUISITE,
    RELATED,
    /** 易混淆，交错练习优先混这类节点 */
    CONTRASTS
}
