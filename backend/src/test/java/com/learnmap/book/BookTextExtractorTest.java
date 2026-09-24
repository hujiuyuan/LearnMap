package com.learnmap.book;

import com.learnmap.ai.AiProvider;
import com.learnmap.book.BookTextExtractor.Extracted;
import com.learnmap.book.BookTextExtractor.Upload;
import com.learnmap.book.TextBookParser.ParsedSection;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookTextExtractorTest {

    final AiProvider ai = mock(AiProvider.class);
    final BookTextExtractor extractor = new BookTextExtractor(ai);

    @Test
    void epubFollowsSpineOrderAndUsesHeadings() throws Exception {
        Extracted e = extractor.extract(FileFormat.EPUB, List.of(new Upload("b.epub", BookFixtures.epub())));
        assertThat(e.kind()).isEqualTo(ContentKind.TEXT);
        List<ParsedSection> sections = TextBookParser.parse(e.text());
        assertThat(sections).extracting(ParsedSection::title).containsExactly("第一章 方程", "1.1 等式的性质", "1.2 移项");
        assertThat(sections).extracting(ParsedSection::level).containsExactly(1, 2, 2);
        assertThat(sections.get(1).chunks().get(0)).contains("仍然成立", "x = 2");
    }

    @Test
    void docxHeadingStylesBecomeSections() throws Exception {
        Extracted e = extractor.extract(FileFormat.DOCX, List.of(new Upload("b.docx", BookFixtures.docx())));
        List<ParsedSection> sections = TextBookParser.parse(e.text());
        assertThat(sections).extracting(ParsedSection::title).containsExactly("第一章 方程", "等式的性质", "移项");
        assertThat(sections.get(2).chunks()).containsExactly("把某一项从等号一边移到另一边，要改变符号。");
    }

    @Test
    void textPdfIsReadDirectly() throws Exception {
        Extracted e = extractor.extract(FileFormat.PDF, List.of(new Upload("b.pdf", BookFixtures.textPdf())));
        assertThat(e.kind()).isEqualTo(ContentKind.TEXT);
        assertThat(TextBookParser.parse(e.text())).extracting(ParsedSection::title)
                .containsExactly("1.1 Properties of equality", "1.2 Transposition");
    }

    @Test
    void scannedPdfAndPhotosGoThroughOcr() throws Exception {
        when(ai.ocr(any(), eq("image/png"))).thenReturn("第一章 方程\n\n等式两边同时加上同一个数，等式仍然成立。");
        Extracted e = extractor.extract(FileFormat.PDF, List.of(new Upload("scan.pdf", BookFixtures.scannedPdf(2))));
        assertThat(e.kind()).isEqualTo(ContentKind.IMAGE);
        verify(ai, times(2)).ocr(any(), eq("image/png"));
        assertThat(TextBookParser.parse(e.text()).get(0).title()).isEqualTo("第一章 方程");

        when(ai.ocr(any(), eq("image/jpeg"))).thenReturn("第二章 函数");
        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(zipped)) {
            for (String n : List.of("p2.jpg", "p1.jpg", "notes.txt")) {
                zip.putNextEntry(new ZipEntry(n));
                zip.write(new byte[]{1});
                zip.closeEntry();
            }
        }
        Extracted photos = extractor.extract(FileFormat.IMAGES, List.of(new Upload("pages.zip", zipped.toByteArray()),
                new Upload("p3.jpg", new byte[]{2})));
        assertThat(photos.kind()).isEqualTo(ContentKind.IMAGE);
        verify(ai, times(3)).ocr(any(), eq("image/jpeg"));
    }
}
