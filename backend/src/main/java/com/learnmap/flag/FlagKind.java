package com.learnmap.flag;

public enum FlagKind {
    /** 辅导者：诊断说不会，但他其实会 */
    DIAGNOSIS_WRONG,
    /** 辅导者：这块他确实不会 */
    REALLY_WEAK,
    /** 系统：第一性原理补缺两轮都没通过，需要辅导者帮忙 */
    STUCK
}
