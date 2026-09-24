package com.learnmap.plan;

/** 每天的时间怎么切块 */
public enum Rhythm {
    /** 番茄钟：25 分钟一块 */
    POMODORO(25),
    /** 碎片时间：10 分钟一块 */
    MICRO(10),
    /** 整块时间：一块 90 分钟 */
    LONG(90);

    public final int blockMinutes;

    Rhythm(int blockMinutes) {
        this.blockMinutes = blockMinutes;
    }
}
