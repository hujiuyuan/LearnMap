package com.learnmap.practice;

public enum PracticeMode {
    /** 刻意练习：针对根因节点验证 → 专项练习 → 3 天后复验 */
    DELIBERATE,
    /** 交错练习：几个学过、易混淆的节点的题混在一起，不提示考哪个知识点 */
    INTERLEAVED,
    /** 第一性原理：目标 → 必需知识点 → 哪里不会补哪里、递归往下补 */
    FIRST_PRINCIPLES
}
