package com.learnmap.note;

public enum NoteKind {
    /** 康奈尔：线索栏 + 笔记栏 + 总结栏，线索栏的问题自动变成主动回忆卡片 */
    CORNELL,
    /** 卡片盒：一张卡片一个观点，用 [[标题]] 互相链接 */
    ZETTEL,
    /** 内容地图：一个主题（一章）的索引页 */
    MOC
}
