package com.learnmap.palace;

import com.learnmap.card.Rating;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PalaceRulesTest {

    @Test
    void lociMustBeEnoughDistinctAndNonEmpty() {
        assertThat(PalaceRules.lociIssues(List.of("a", "b", "c"))).anyMatch(s -> s.contains("至少"));
        assertThat(PalaceRules.lociIssues(List.of("鞋柜", "沙发", " 鞋柜 ", "电视", "餐桌"))).anyMatch(s -> s.contains("重复"));
        assertThat(PalaceRules.lociIssues(List.of("鞋柜", "沙发", "", "电视", "餐桌"))).anyMatch(s -> s.contains("空"));
        assertThat(PalaceRules.lociIssues(List.of("鞋柜", "沙发", "茶几", "电视", "餐桌"))).isEmpty();
    }

    @Test
    void recallIsLenientAboutWording() {
        assertThat(PalaceRules.recalled("移项", "移项", "移项")).isTrue();
        assertThat(PalaceRules.recalled("移项", "移项", "这里放的是移项")).isTrue();
        assertThat(PalaceRules.recalled("去分母、去括号、移项、合并同类项", null, "去分母 去括号 移项 合并同类项")).isTrue();
        assertThat(PalaceRules.recalled("去分母、去括号、移项、合并同类项", null, "去括号")).isFalse();
        assertThat(PalaceRules.recalled("移项", "移项", "")).isFalse();
        // 漏了一两个虚词也算想起来了
        assertThat(PalaceRules.recalled("移项要变号", null, "移项变号")).isTrue();
        assertThat(PalaceRules.recalled("去分母", null, "去括号")).isFalse();
    }

    @Test
    void locusNamesMatchByContainment() {
        assertThat(PalaceRules.sameLocus("门口鞋柜", "鞋柜")).isTrue();
        assertThat(PalaceRules.sameLocus("沙发", "客厅沙发")).isTrue();
        assertThat(PalaceRules.sameLocus("沙发", "电视")).isFalse();
        assertThat(PalaceRules.sameLocus("沙发", " ")).isFalse();
    }

    @Test
    void ratingFollowsAccuracy() {
        assertThat(PalaceRules.rating(10, 10)).isEqualTo(Rating.GOOD);
        assertThat(PalaceRules.rating(7, 10)).isEqualTo(Rating.HARD);
        assertThat(PalaceRules.rating(3, 10)).isEqualTo(Rating.AGAIN);
    }
}
