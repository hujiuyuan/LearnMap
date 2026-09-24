package com.learnmap.practice;

public enum PracticePhase {
    /** 刻意练习：先验证是不是真的不会 */
    VERIFY,
    /** 刻意练习：专项练习 */
    PRACTICE,
    /** 交错练习：混合题 */
    MIXED,
    /** 第一性原理：数据不足的知识点先出探测题 */
    PROBE,
    /** 第一性原理：从底层往上逐个补 */
    LEARN,
    /** 第一性原理：回到目标本身，原题 + 变式 */
    GOAL,
    DONE
}
