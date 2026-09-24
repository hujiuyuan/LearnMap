package com.learnmap.profile;

import com.learnmap.graph.NodeType;
import com.learnmap.question.Ability;

import java.time.LocalDate;
import java.util.List;

/** 五张画像（产品方案 5.4），全部由规则和统计算出，不用 AI */
public record LearnerProfile(Long learnerId, LocalDate asOf, Knowledge knowledge, Abilities abilities, Errors errors,
                             Modes modes, Behavior behavior) {

    // ---------- 知识画像 ----------

    public record Knowledge(int total, int mastered, int learning, int weak, int unsure, int untouched,
                            List<ChapterStat> chapters, List<NodeStat> nodes) {
    }

    public record ChapterStat(Long graphId, Long chapterId, String title, int nodes, int mastered, double avgMastery) {
    }

    public record NodeStat(Long id, Long graphId, Long chapterId, String name, NodeType type, Double mastery,
                           Double confidence, NodeStatus status) {
    }

    // ---------- 能力画像 ----------

    /**
     * @param expression  费曼讲解的平均分（有人工复核的用复核分）
     * @param calibration 答前自评和实际对错的偏差
     * @param solutions   做对时用的解法：看出“总是硬算”这类偏好
     */
    public record Abilities(List<AbilityStat> abilities, Double expression, int feynmanCount, Calibration calibration,
                            List<SolutionUse> solutions) {
    }

    public record AbilityStat(Ability ability, String label, int attempts, double accuracy) {
    }

    /** @param overconfident 说“有把握”却做错；underconfident 说“不确定 / 不会”却做对 */
    public record Calibration(int total, int overconfident, int underconfident, Double calibratedRate) {
    }

    public record SolutionUse(String name, int correct) {
    }

    // ---------- 错误画像 ----------

    public record Errors(int wrongTotal, int unlabeled, List<ErrorStat> byType) {
    }

    public record ErrorStat(String type, String label, int count, double share) {
    }

    // ---------- 学习方式画像 ----------

    public record Modes(List<ModeStat> stats, List<Completion> completion, List<String> suggestions) {
    }

    /**
     * 某种学习方式在某类节点上的效果
     *
     * @param gainPer10Min 每 10 分钟平均提升多少掌握度（按证据回放掌握度引擎算）
     * @param retentionRate 用这种方式学会的节点，延时复测还记得的比例
     */
    public record ModeStat(String mode, String modeLabel, NodeType nodeType, int evidences, double minutes,
                           Double gainPer10Min, int retests, int retained, Double retentionRate) {
    }

    public record Completion(String mode, String modeLabel, int started, int completed, Double rate) {
    }

    // ---------- 学习行为画像 ----------

    /**
     * @param planCompletionRate 到今天为止该做的计划任务里完成的比例（低于 50% 说明计划太重）
     * @param lateRate           完成的任务里晚于计划日期完成的比例
     * @param hourHistogram      24 小时每小时的作答数
     */
    public record Behavior(int activeDays14, int streak, Double planCompletionRate, int tasksDue, int tasksDone,
                           int tasksSkipped, int tasksMissed, Double lateRate, int pomodoros, int pomodorosCompleted,
                           Double pomodoroCompletionRate, Double avgInterruptions, int focusMinutes14,
                           String preferredSlot, List<Integer> hourHistogram, List<String> notes) {
    }

    /** 学习者自己看的正向信息（产品方案 5.5）：点亮了多少、连续学了几天、最适合的方式 */
    public record Highlights(int lit, int total, int streak, int activeDays14, String bestMode) {
    }
}
