package com.learnmap.plan;

public enum TaskType {
    /** 复习今天到期的卡片 */
    REVIEW_CARDS,
    /** 做到期的延时复测 */
    RETEST,
    /** 初学：读定义、看例子，节点的卡片随之进入复习 */
    NEW_LEARN,
    /** 小测几道题 */
    QUIZ,
    FEYNMAN,
    DELIBERATE,
    INTERLEAVED,
    FIRST_PRINCIPLES
}
