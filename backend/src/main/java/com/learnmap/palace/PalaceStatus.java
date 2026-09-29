package com.learnmap.palace;

public enum PalaceStatus {
    /** 选好地方，还在定地点 */
    BUILDING,
    /** 地点定好了，在走熟路线（顺背、倒背、随机问下一个） */
    DRILLING,
    /** 路线走熟了，宫殿建成，可以挂内容 */
    READY
}
