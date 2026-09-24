package com.learnmap.book;

public enum Visibility {
    /** 学习者自己上传：只有本人（和他的辅导者）可见 */
    PRIVATE,
    /** 辅导者上传：辅导者和名下学习者可见 */
    TUTOR_GROUP,
    /** 平台预置书：所有用户可见 */
    PUBLIC
}
