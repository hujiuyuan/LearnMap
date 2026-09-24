package com.learnmap.graph;

public enum GraphKind {
    /** 书本图谱：一本书每生成一次就是一个版本 */
    MASTER,
    /** 学习者自己的副本：分配给学习者时从 MASTER 复制，之后的修正只改副本 */
    LEARNER_COPY
}
