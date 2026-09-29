package com.learnmap.profile;

import com.learnmap.mastery.NodeMastery;

/** 节点状态（产品方案 2.6），和前端地图的颜色一致 */
public enum NodeStatus {
    UNTOUCHED("未学"), UNSURE("数据不足"), MASTERED("已掌握"), LEARNING("学习中"), WEAK("疑似薄弱");

    public final String label;

    NodeStatus(String label) {
        this.label = label;
    }

    public static NodeStatus of(NodeMastery m) {
        if (m == null) {
            return UNTOUCHED;
        }
        if (m.getConfidence() < 0.3) {
            return UNSURE;
        }
        if (m.getMastery() >= 0.8) {
            return MASTERED;
        }
        return m.getMastery() < 0.5 ? WEAK : LEARNING;
    }
}
