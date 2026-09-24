package com.learnmap.mastery;

import com.learnmap.evidence.SelfConfidence;

/**
 * 规则计算的掌握度（不用 AI，结果可解释）：
 * <ul>
 *   <li>每条证据给出一个“目标值”：做对 1，做错 0，部分对取得分；按难度修正——
 *       难题做对算满分、简单题做对只算 0.85；简单题做错是 0、难题做错保留 0.3。</li>
 *   <li>答前自评“不会”却做对，按蒙对处理，目标值减半。</li>
 *   <li>Mastery 向目标值移动，步长前几条证据大、之后固定 0.25（越新的证据权重越高）。</li>
 *   <li>Confidence = 1 − e^(−n/4)，证据越多越可信；未校对节点上限 0.6，统一用“疑似 / 建议验证”的话术。</li>
 * </ul>
 */
public final class MasteryEngine {

    static final double MIN_STEP = 0.25;
    static final double UNREVIEWED_CONFIDENCE_CAP = 0.6;
    /** 高于这个值且 Confidence 足够时，计划和第一性原理补缺会跳过该节点 */
    public static final double MASTERED = 0.8;
    public static final double CONFIDENT = 0.6;

    public record State(double mastery, double confidence, int evidenceCount) {
        public static final State EMPTY = new State(0, 0, 0);

        public boolean mastered() {
            return mastery >= MASTERED && confidence >= CONFIDENT;
        }
    }

    private MasteryEngine() {
    }

    /**
     * @param score      0~1，本次得分
     * @param difficulty 0~1，题目难度
     * @param reviewed   节点是否经过人工校对
     */
    public static State update(State prev, double score, double difficulty, SelfConfidence selfConfidence, boolean reviewed) {
        double target = target(score, difficulty, selfConfidence);
        int n = prev.evidenceCount() + 1;
        double step = Math.max(MIN_STEP, 1.0 / n);
        double mastery = clamp(prev.mastery() + step * (target - prev.mastery()));
        double confidence = 1 - Math.exp(-n / 4.0);
        if (!reviewed) {
            confidence = Math.min(confidence, UNREVIEWED_CONFIDENCE_CAP);
        }
        return new State(mastery, confidence, n);
    }

    static double target(double score, double difficulty, SelfConfidence selfConfidence) {
        double d = clamp(difficulty);
        double ceiling = 0.85 + 0.15 * d;  // 做对时能拿到的上限
        double floor = 0.3 * d;            // 做错时保留的下限
        double t = floor + clamp(score) * (ceiling - floor);
        if (selfConfidence == SelfConfidence.DONT_KNOW && score > 0.5) {
            t *= 0.5;
        }
        return t;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
