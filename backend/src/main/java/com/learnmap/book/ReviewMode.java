package com.learnmap.book;

public enum ReviewMode {
    /** 生成完直接发布，节点标“未校对” */
    NONE,
    /** 先在图谱编辑器里校对，再发布 */
    REVIEW_BEFORE_PUBLISH
}
