package com.learnmap.book;

import com.learnmap.book.TextBookParser.ParsedSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextBookParserTest {

    @Test
    void parsesMarkdownHeadingsIntoLevels() {
        List<ParsedSection> s = TextBookParser.parse("""
                # 第一章 函数

                ## 1.1 函数的概念

                设 A、B 是非空数集。

                第二段。

                ### 1.1.1 定义域
                自变量的取值范围。
                """);
        assertThat(s).extracting(ParsedSection::title).containsExactly("第一章 函数", "1.1 函数的概念", "1.1.1 定义域");
        assertThat(s).extracting(ParsedSection::level).containsExactly(1, 2, 3);
        assertThat(s.get(0).chunks()).isEmpty();
        assertThat(s.get(1).chunks()).containsExactly("设 A、B 是非空数集。\n\n第二段。");
    }

    @Test
    void recognisesChineseAndNumberedHeadingsInPlainText() {
        List<ParsedSection> s = TextBookParser.parse("""
                第三章 细胞
                第一节 细胞膜
                细胞膜主要由脂质和蛋白质组成。
                3.2 细胞核
                细胞核是遗传信息库。
                """);
        assertThat(s).extracting(ParsedSection::level).containsExactly(1, 2, 2);
        assertThat(s.get(2).chunks()).containsExactly("细胞核是遗传信息库。");
    }

    @Test
    void textBeforeFirstHeadingBecomesPreface() {
        List<ParsedSection> s = TextBookParser.parse("写在前面的话。\n\n# 正文\n内容");
        assertThat(s.get(0).title()).isEqualTo("前言");
        assertThat(s.get(0).chunks()).containsExactly("写在前面的话。");
    }

    @Test
    void splitsLongSectionsIntoBoundedChunks() {
        String para = "字".repeat(500);
        List<String> chunks = TextBookParser.toChunks(List.of(para, para, "短"));
        assertThat(chunks).hasSize(2);
        assertThat(chunks).allMatch(c -> c.length() <= TextBookParser.MAX_CHUNK_CHARS);
    }
}
