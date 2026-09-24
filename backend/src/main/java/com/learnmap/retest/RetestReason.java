package com.learnmap.retest;

public enum RetestReason {
    RETENTION_3D(3), RETENTION_7D(7), RETENTION_21D(21),
    /** 刻意练习结束 3 天后复验 */
    DELIBERATE_RECHECK(3);

    public final int days;

    RetestReason(int days) {
        this.days = days;
    }
}
