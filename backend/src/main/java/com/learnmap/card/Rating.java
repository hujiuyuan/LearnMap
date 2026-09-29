package com.learnmap.card;

/** 翻面后的自评：忘了 / 困难 / 记得 / 轻松 */
public enum Rating {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    final int grade;

    Rating(int grade) {
        this.grade = grade;
    }
}
