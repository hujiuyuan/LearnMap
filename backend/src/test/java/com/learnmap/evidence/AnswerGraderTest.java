package com.learnmap.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerGraderTest {

    @Test
    void numericAnswersCompareByValue() {
        assertThat(AnswerGrader.matches("1/2", "0.5")).isTrue();
        assertThat(AnswerGrader.matches("0.5", "2/4")).isTrue();
        assertThat(AnswerGrader.matches("3", "3.0")).isTrue();
        assertThat(AnswerGrader.matches("1/3", "0.33")).isFalse();
    }

    @Test
    void textAnswersIgnoreWhitespaceCaseAndFullWidth() {
        assertThat(AnswerGrader.matches("x = -3", "X=-3")).isTrue();
        assertThat(AnswerGrader.matches("ABC", "ａｂｃ")).isTrue();
        assertThat(AnswerGrader.matches("正确", " 正确 ")).isTrue();
        assertThat(AnswerGrader.matches("正确", "错误")).isFalse();
    }

    @Test
    void nullOrDivideByZeroNeverMatches() {
        assertThat(AnswerGrader.matches("1", null)).isFalse();
        assertThat(AnswerGrader.matches("1/0", "1/0")).isTrue(); // 字面相同
        assertThat(AnswerGrader.number("1/0")).isNull();
    }
}
