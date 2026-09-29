package com.learnmap.retest;

public enum RetestReason {
    RETENTION_3D(3), RETENTION_7D(7), RETENTION_21D(21),
    /** 刻意练习结束 3 天后复验 */
    DELIBERATE_RECHECK(3),
    /** 辅导者标记“诊断不对”后马上复测一次来验证 */
    TUTOR_CHECK(0);

    public final int days;

    RetestReason(int days) {
        this.days = days;
    }
}
