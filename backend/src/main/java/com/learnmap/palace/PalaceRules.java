package com.learnmap.palace;

import com.learnmap.card.Rating;
import com.learnmap.evidence.AnswerGrader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 记忆宫殿的纯规则：地点检查、回忆判分、重走宫殿的评级 */
public final class PalaceRules {

    /** 先从 5 个地点的小宫殿开始，熟练后可以扩到 30 个 */
    public static final int MIN_LOCI = 5;
    public static final int MAX_LOCI = 30;
    /** 随机问“下一个是什么”要连续答对几次 */
    public static final int RANDOM_NEEDED = 3;

    private PalaceRules() {
    }

    /** 检查地点：数量、不能空、不能重复（重复的地点回忆时会串） */
    public static List<String> lociIssues(List<String> names) {
        List<String> issues = new ArrayList<>();
        if (names.size() < MIN_LOCI) {
            issues.add("至少定 " + MIN_LOCI + " 个地点，先从小宫殿开始");
        }
        if (names.size() > MAX_LOCI) {
            issues.add("最多 " + MAX_LOCI + " 个地点，太多可以再建一座宫殿");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i) == null ? "" : names.get(i).strip();
            if (n.isEmpty()) {
                issues.add("第 " + (i + 1) + " 个地点是空的");
            } else if (n.length() > 100) {
                issues.add("第 " + (i + 1) + " 个地点名字太长了，用几个字就好");
            } else if (!seen.add(key(n))) {
                issues.add("「" + n + "」重复了，每个地点要不一样，不然回忆时会串");
            }
        }
        return issues;
    }

    /**
     * 回忆判分：和原内容一致，或者说出了知识点名称，或者用字大体一样（两字片段重合 ≥ 60%）都算对。
     * 记忆宫殿记的是“是什么”，不要求一字不差。
     */
    public static boolean recalled(String content, String nodeName, String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        if (AnswerGrader.matches(content, answer) || (nodeName != null && AnswerGrader.matches(nodeName, answer))) {
            return true;
        }
        String a = key(answer);
        if (nodeName != null && key(nodeName).length() >= 2 && a.contains(key(nodeName))) {
            return true;
        }
        return overlap(content, answer) >= 0.6;
    }

    /** 地点名：一致，或者一个包含另一个（“鞋柜”和“门口鞋柜”都算对） */
    public static boolean sameLocus(String expected, String answer) {
        String e = key(expected);
        String a = key(answer == null ? "" : answer);
        if (a.isEmpty()) {
            return false;
        }
        return e.equals(a) || (a.length() >= 2 && e.contains(a)) || (e.length() >= 2 && a.contains(e));
    }

    /** 按顺序回忆的正确率 → FSRS 评级 */
    public static Rating rating(int correct, int total) {
        double r = total == 0 ? 0 : correct / (double) total;
        if (r >= 0.9) {
            return Rating.GOOD;
        }
        if (r >= 0.6) {
            return Rating.HARD;
        }
        return Rating.AGAIN;
    }

    static double overlap(String expected, String answer) {
        Set<String> e = bigrams(expected);
        if (e.isEmpty()) {
            return 0;
        }
        Set<String> a = bigrams(answer);
        long hit = e.stream().filter(a::contains).count();
        return hit / (double) e.size();
    }

    static Set<String> bigrams(String s) {
        String t = key(s);
        Set<String> out = new HashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) {
            out.add(t.substring(i, i + 2));
        }
        return out;
    }

    static String key(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\p{Punct}，。、；：？！“”‘’（）【】《》·]", "").toLowerCase(Locale.ROOT);
    }
}
