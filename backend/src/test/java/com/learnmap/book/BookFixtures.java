package com.learnmap.book;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 测试用：现场生成 EPUB / Word / PDF 文件 */
public final class BookFixtures {

    private BookFixtures() {
    }

    public static byte[] epub() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "mimetype", "application/epub+zip");
            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>""");
            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest>
                        <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
                    </package>""");
            put(zip, "OEBPS/text/ch1.xhtml", """
                    <html><body><h1>第一章 方程</h1><h2>1.1 等式的性质</h2>
                    <p>等式两边同时加上或减去同一个数，等式仍然成立。</p>
                    <ul><li>例如 x + 1 = 3，两边减 1 得 x = 2。</li></ul></body></html>""");
            put(zip, "OEBPS/text/ch2.xhtml", """
                    <html><body><h2>1.2 移项</h2><p>把某一项从等号一边移到另一边，要改变符号。</p></body></html>""");
        }
        return bytes.toByteArray();
    }

    public static byte[] docx() throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFStyles styles = doc.createStyles();
            for (int level = 1; level <= 2; level++) {
                CTStyle ct = CTStyle.Factory.newInstance();
                ct.setStyleId("Heading" + level);
                ct.addNewName().setVal("heading " + level);
                ct.setType(STStyleType.PARAGRAPH);
                styles.addStyle(new XWPFStyle(ct));
            }
            paragraph(doc, "Heading1", "第一章 方程");
            paragraph(doc, "Heading2", "等式的性质");
            paragraph(doc, null, "等式两边同时加上或减去同一个数，等式仍然成立。");
            paragraph(doc, "Heading2", "移项");
            paragraph(doc, null, "把某一项从等号一边移到另一边，要改变符号。");
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** 文字版 PDF（标准字体不带中文，用英文正文，章节靠 “1.1 标题” 识别） */
    public static byte[] textPdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(18);
                cs.newLineAtOffset(50, 700);
                for (String line : List.of("1.1 Properties of equality", "", "Adding the same number to both sides keeps the equation true.",
                        "", "1.2 Transposition", "", "Moving a term to the other side changes its sign.")) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 没有文字层的 PDF（模拟扫描版） */
    public static byte[] scannedPdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void paragraph(XWPFDocument doc, String style, String text) {
        var p = doc.createParagraph();
        if (style != null) {
            p.setStyle(style);
        }
        p.createRun().setText(text);
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
