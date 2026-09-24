package com.learnmap.book;

import com.learnmap.ai.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 把各种格式的书转成带标题的纯文本（Markdown 风格的 # 标题），再交给 {@link TextBookParser} 解析章节。
 * <ul>
 *   <li>EPUB：按 OPF 的阅读顺序读 XHTML，h1~h3 当作章节标题；</li>
 *   <li>Word（.docx）：“标题 1~3”样式当作章节标题；</li>
 *   <li>PDF：有文字层的直接取文字（章节靠“第 X 章 / 1.1”这类标题识别）；每页平均不到 {@value #SCANNED_CHARS_PER_PAGE} 个字时当作扫描版，逐页渲染成图片走 OCR；</li>
 *   <li>图片（拍的书页）：逐张 OCR。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookTextExtractor {

    static final int SCANNED_CHARS_PER_PAGE = 30;
    static final int MAX_OCR_PAGES = 300;
    static final float OCR_DPI = 150;

    private final AiProvider ai;

    /** @param kind 这本书是文字类还是图片类（需要 OCR） */
    public record Extracted(String text, ContentKind kind) {
    }

    public record Upload(String filename, byte[] content) {
    }

    public Extracted extract(FileFormat format, List<Upload> files) throws IOException {
        Upload first = files.get(0);
        return switch (format) {
            case TXT, MARKDOWN -> new Extracted(new String(first.content(), StandardCharsets.UTF_8), ContentKind.TEXT);
            case EPUB -> new Extracted(epub(first.content()), ContentKind.TEXT);
            case DOCX -> new Extracted(docx(first.content()), ContentKind.TEXT);
            case PDF -> pdf(first.content());
            case IMAGES -> new Extracted(images(files), ContentKind.IMAGE);
        };
    }

    // ---------- EPUB ----------

    static String epub(byte[] content) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    entries.put(e.getName(), zip.readAllBytes());
                }
            }
        }
        byte[] container = entries.get("META-INF/container.xml");
        if (container == null) {
            throw new IllegalArgumentException("不是有效的 EPUB 文件（缺少 container.xml）");
        }
        String opfPath = Jsoup.parse(new String(container, StandardCharsets.UTF_8), "", Parser.xmlParser())
                .select("rootfile").attr("full-path");
        byte[] opfBytes = entries.get(opfPath);
        if (opfBytes == null) {
            throw new IllegalArgumentException("不是有效的 EPUB 文件（找不到 " + opfPath + "）");
        }
        String base = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";
        Document opf = Jsoup.parse(new String(opfBytes, StandardCharsets.UTF_8), "", Parser.xmlParser());
        Map<String, String> manifest = new HashMap<>();
        for (Element item : opf.select("manifest > item")) {
            manifest.put(item.attr("id"), item.attr("href"));
        }
        StringBuilder out = new StringBuilder();
        for (Element ref : opf.select("spine > itemref")) {
            String href = manifest.get(ref.attr("idref"));
            byte[] page = href == null ? null : entries.get(base + href);
            if (page != null) {
                html(Jsoup.parse(new String(page, StandardCharsets.UTF_8)), out);
            }
        }
        return out.toString();
    }

    private static void html(Document doc, StringBuilder out) {
        for (Element el : doc.body().select("h1, h2, h3, h4, p, li")) {
            String text = el.text().strip();
            if (text.isEmpty() || (el.tagName().equals("li") && el.selectFirst("p") != null)) {
                continue; // 列表项里有段落时，段落会单独取
            }
            switch (el.tagName()) {
                case "h1" -> out.append("# ");
                case "h2" -> out.append("## ");
                case "h3" -> out.append("### ");
                default -> {
                }
            }
            out.append(text).append("\n\n");
        }
    }

    // ---------- Word ----------

    static String docx(byte[] content) throws IOException {
        StringBuilder out = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText().strip();
                if (text.isEmpty()) {
                    continue;
                }
                int level = headingLevel(doc, p);
                if (level > 0) {
                    out.append("#".repeat(level)).append(' ');
                }
                out.append(text).append("\n\n");
            }
        }
        return out.toString();
    }

    /** “Heading 1 / 标题 1” 这类样式 → 1~3；不是标题返回 0 */
    private static int headingLevel(XWPFDocument doc, XWPFParagraph p) {
        String id = p.getStyleID();
        if (id == null) {
            return 0;
        }
        XWPFStyle style = doc.getStyles() == null ? null : doc.getStyles().getStyle(id);
        String name = (style != null && style.getName() != null ? style.getName() : id).toLowerCase(Locale.ROOT).replace(" ", "");
        for (String prefix : List.of("heading", "标题")) {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                char c = name.charAt(prefix.length());
                if (c >= '1' && c <= '3') {
                    return c - '0';
                }
            }
        }
        return 0;
    }

    // ---------- PDF ----------

    Extracted pdf(byte[] content) throws IOException {
        try (PDDocument doc = Loader.loadPDF(content)) {
            int pages = doc.getNumberOfPages();
            String text = new PDFTextStripper().getText(doc);
            if (pages == 0 || text.strip().length() >= SCANNED_CHARS_PER_PAGE * pages) {
                return new Extracted(text, ContentKind.TEXT);
            }
            // 扫描版：逐页渲染成图片走 OCR
            if (pages > MAX_OCR_PAGES) {
                throw new IllegalArgumentException("扫描版 PDF 最多 " + MAX_OCR_PAGES + " 页");
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                ImageIO.write(img, "png", png);
                out.append(ai.ocr(png.toByteArray(), "image/png")).append("\n\n");
            }
            log.info("扫描版 PDF：{} 页走了 OCR", pages);
            return new Extracted(out.toString(), ContentKind.IMAGE);
        }
    }

    // ---------- 图片 ----------

    /** 逐张 OCR；也可以传一个装着书页照片的 zip，按文件名顺序识别 */
    private String images(List<Upload> files) throws IOException {
        List<Upload> pages = new ArrayList<>();
        for (Upload f : files) {
            if (f.filename() != null && f.filename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                pages.addAll(unzipImages(f.content()));
            } else {
                pages.add(f);
            }
        }
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("没有找到书页图片");
        }
        if (pages.size() > MAX_OCR_PAGES) {
            throw new IllegalArgumentException("一次最多识别 " + MAX_OCR_PAGES + " 张图片");
        }
        List<String> texts = new ArrayList<>();
        for (Upload p : pages) {
            texts.add(ai.ocr(p.content(), mimeOf(p.filename())));
        }
        return String.join("\n\n", texts);
    }

    private static List<Upload> unzipImages(byte[] zipBytes) throws IOException {
        List<Upload> out = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String n = e.getName().toLowerCase(Locale.ROOT);
                if (!e.isDirectory() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))) {
                    out.add(new Upload(e.getName(), zip.readAllBytes()));
                }
            }
        }
        out.sort(Comparator.comparing(Upload::filename));
        return out;
    }

    static String mimeOf(String filename) {
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }
}
