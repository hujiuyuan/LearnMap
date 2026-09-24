package com.learnmap.card;

import java.time.Duration;

/**
 * FSRS-4.5 调度算法（默认参数），按“目标记忆保持率 90%”安排下次复习。
 * 简化：不做分钟级的学习步骤，只有“忘了”时 10 分钟后重来一次。
 * 参考 https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 */
public final class Fsrs {

    static final double[] W = {0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031, 1.6474,
            0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755};
    static final double DECAY = -0.5;
    static final double FACTOR = 19.0 / 81.0;
    static final double REQUEST_RETENTION = 0.9;
    static final double MAX_INTERVAL_DAYS = 365;
    static final Duration RELEARN_DELAY = Duration.ofMinutes(10);

    public record Memory(CardState state, double stability, double difficulty) {
    }

    public record Result(CardState state, double stability, double difficulty, Duration interval) {
    }

    private Fsrs() {
    }

    /** @param elapsedDays 距上次复习的天数（新卡忽略） */
    public static Result review(Memory m, Rating rating, double elapsedDays) {
        int g = rating.grade;
        double s;
        double d;
        if (m.state() == CardState.NEW) {
            s = W[g - 1];
            d = initDifficulty(g);
        } else {
            double r = retrievability(Math.max(0, elapsedDays), m.stability());
            d = nextDifficulty(m.difficulty(), g);
            s = rating == Rating.AGAIN
                    ? forgetStability(m.difficulty(), m.stability(), r)
                    : recallStability(m.difficulty(), m.stability(), r, rating);
        }
        if (rating == Rating.AGAIN) {
            return new Result(CardState.RELEARNING, s, d, RELEARN_DELAY);
        }
        double days = Math.min(MAX_INTERVAL_DAYS, Math.max(1, Math.round(interval(s))));
        return new Result(CardState.REVIEW, s, d, Duration.ofDays((long) days));
    }

    static double retrievability(double elapsedDays, double stability) {
        return Math.pow(1 + FACTOR * elapsedDays / stability, DECAY);
    }

    static double interval(double stability) {
        return stability / FACTOR * (Math.pow(REQUEST_RETENTION, 1 / DECAY) - 1);
    }

    static double initDifficulty(int g) {
        return clampD(W[4] - (g - 3) * W[5]);
    }

    static double nextDifficulty(double d, int g) {
        double next = d - W[6] * (g - 3);
        return clampD(W[7] * initDifficulty(3) + (1 - W[7]) * next);
    }

    static double recallStability(double d, double s, double r, Rating rating) {
        double hard = rating == Rating.HARD ? W[15] : 1;
        double easy = rating == Rating.EASY ? W[16] : 1;
        return s * (Math.exp(W[8]) * (11 - d) * Math.pow(s, -W[9]) * (Math.exp(W[10] * (1 - r)) - 1) * hard * easy + 1);
    }

    static double forgetStability(double d, double s, double r) {
        return W[11] * Math.pow(d, -W[12]) * (Math.pow(s + 1, W[13]) - 1) * Math.exp(W[14] * (1 - r));
    }

    private static double clampD(double d) {
        return Math.max(1, Math.min(10, d));
    }
}
