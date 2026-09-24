package com.learnmap.question;

public enum QuestionSource {
    /** 书中原题，质量最高 */
    BOOK,
    /** AI 按节点生成 */
    AI,
    /** 辅导者 / 运维录入 */
    MANUAL,
    /** 学习者自己的错题，只进本人题库 */
    WRONG_BOOK
}
