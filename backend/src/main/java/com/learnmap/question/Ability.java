package com.learnmap.question;

/** 题目考查的能力，用于能力画像 */
public enum Ability {
    UNDERSTANDING("概念理解"),
    RECALL("记忆"),
    CALCULATION("计算"),
    READING("审题"),
    MODELING("建模"),
    REASONING("推理证明"),
    EXPRESSION("表达");

    public final String label;

    Ability(String label) {
        this.label = label;
    }
}
