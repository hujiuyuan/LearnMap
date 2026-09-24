package com.learnmap.user;

/** 按“自己学还是管别人学”区分，不按学生 / 成人区分。 */
public enum Role {
    /** 学习者：自己学的人，学生、成人都可以 */
    LEARNER,
    /** 辅导者：管理名下学习者，家长、老师、家教 */
    TUTOR,
    /** 平台运维：上传预置书、校对图谱 */
    OPS
}
