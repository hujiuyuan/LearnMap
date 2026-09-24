package com.learnmap.book;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯文本 / Markdown 书的结构解析：按标题切出 章 → 节 → 小节，再把正文切成原文块。
 * 规则解析，不用 AI。EPUB、PDF、图片类（OCR）以后由导入流水线服务先转成文本再进这里。
 */
public final class TextBookParser {

    /** 单个原文块的目标上限，超过就另起一块 */
    static final int MAX_CHUNK_CHARS = 800;

    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern CN_CHAPTER = Pattern.compile("^第[一二三四五六七八九十百零〇\\d]+章\\s*.*$");
    private static final Pattern CN_SECTION = Pattern.compile("^第[一二三四五六七八九十百零〇\\d]+节\\s*.*$");
    private static final Pattern NUM_SUBSECTION = Pattern.compile("^\\d+\\.\\d+\\.\\d+\\s+\\S.*$");
    private static final Pattern NUM_SECTION = Pattern.compile("^\\d+\\.\\d+\\s+\\S.*$");

    public record ParsedSection(String title, int level, List<String> chunks) {
    }

    private TextBookParser() {
    }

    public static List<ParsedSection> parse(String text) {
        List<ParsedSection> sections = new ArrayList<>();
        String title = null;
        int level = 1;
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();

        for (String raw : text.replace("\r\n", "\n").split("\n", -1)) {
            String line = raw.strip();
            Heading heading = heading(line);
            if (heading != null) {
                flushParagraph(paragraph, paragraphs);
                addSection(sections, title, level, paragraphs);
                title = heading.title();
                level = heading.level();
                paragraphs = new ArrayList<>();
            } else if (line.isEmpty()) {
                flushParagraph(paragraph, paragraphs);
            } else {
                if (!paragraph.isEmpty()) {
                    paragraph.append('\n');
                }
                paragraph.append(line);
            }
        }
        flushParagraph(paragraph, paragraphs);
        addSection(sections, title, level, paragraphs);
        return sections;
    }

    private record Heading(String title, int level) {
    }

    private static Heading heading(String line) {
        Matcher md = MD_HEADING.matcher(line);
        if (md.matches()) {
            return new Heading(md.group(2).strip(), md.group(1).length());
        }
        if (line.length() > 60) {
            return null;
        }
        if (CN_CHAPTER.matcher(line).matches()) {
            return new Heading(line, 1);
        }
        if (CN_SECTION.matcher(line).matches()) {
            return new Heading(line, 2);
        }
        if (NUM_SUBSECTION.matcher(line).matches()) {
            return new Heading(line, 3);
        }
        if (NUM_SECTION.matcher(line).matches()) {
            return new Heading(line, 2);
        }
        return null;
    }

    private static void flushParagraph(StringBuilder paragraph, List<String> paragraphs) {
        if (!paragraph.isEmpty()) {
            paragraphs.add(paragraph.toString());
            paragraph.setLength(0);
        }
    }

    private static void addSection(List<ParsedSection> sections, String title, int level, List<String> paragraphs) {
        if (title == null) {
            if (paragraphs.isEmpty()) {
                return;
            }
            title = "前言";
        }
        sections.add(new ParsedSection(title, level, toChunks(paragraphs)));
    }

    /** 相邻的短段落合并成一块，保证每块不超过上限（单段超长时单独成块）。 */
    static List<String> toChunks(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String p : paragraphs) {
            if (!current.isEmpty() && current.length() + p.length() + 2 > MAX_CHUNK_CHARS) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(p);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
