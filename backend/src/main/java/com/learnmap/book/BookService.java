package com.learnmap.book;

import com.learnmap.book.TextBookParser.ParsedSection;
import com.learnmap.common.NotFoundException;
import com.learnmap.graph.GraphGenerationService;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.user.AppUser;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository books;
    private final BookSectionRepository sections;
    private final SourceChunkRepository chunks;
    private final BookAccessPolicy access;
    private final UserService users;
    private final GraphGenerationService graphGeneration;

    public record ImportResult(Book book, KnowledgeGraph graph) {
    }

    /**
     * 导入一本书并生成书本图谱。当前只处理纯文本（TXT / Markdown），同步完成；
     * EPUB、PDF、Word 和图片类（OCR）由后续的导入流水线服务异步处理。
     *
     * @param reviewMode 学习者上传的书一律不校对；辅导者和运维可以选“先校对再发布”
     */
    @Transactional
    public ImportResult importBook(AppUser actor, String title, String author, String filename, byte[] content, ReviewMode reviewMode) {
        FileFormat format = formatOf(filename);
        if (format != FileFormat.TXT && format != FileFormat.MARKDOWN) {
            throw new IllegalArgumentException("暂时只支持 TXT 和 Markdown；" + format + " 的导入流水线还在开发中");
        }
        Book book = new Book();
        book.setTitle(title);
        book.setAuthor(author);
        book.setOwnerId(actor.getId());
        book.setUploaderRole(actor.getRole());
        book.setVisibility(BookAccessPolicy.visibilityFor(actor.getRole()));
        book.setReviewMode(actor.getRole() == Role.LEARNER || reviewMode == null ? ReviewMode.NONE : reviewMode);
        book.setContentKind(ContentKind.TEXT);
        book.setFileFormat(format);
        book.setStatus(BookStatus.PROCESSING);
        book = books.save(book);

        List<ParsedSection> parsed = TextBookParser.parse(new String(content, StandardCharsets.UTF_8));
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("没有解析出任何内容");
        }
        List<BookSection> saved = new ArrayList<>();
        Map<Long, List<SourceChunk>> chunksBySection = new HashMap<>();
        Deque<BookSection> stack = new ArrayDeque<>();
        int chunkOrdinal = 0;
        for (int i = 0; i < parsed.size(); i++) {
            ParsedSection p = parsed.get(i);
            while (!stack.isEmpty() && stack.peek().getLevel() >= p.level()) {
                stack.pop();
            }
            BookSection s = new BookSection();
            s.setBookId(book.getId());
            s.setParentId(stack.isEmpty() ? null : stack.peek().getId());
            s.setTitle(p.title());
            s.setLevel(p.level());
            s.setOrdinal(i);
            s = sections.save(s);
            stack.push(s);
            saved.add(s);
            List<SourceChunk> sectionChunks = new ArrayList<>();
            for (String text : p.chunks()) {
                SourceChunk c = new SourceChunk();
                c.setBookId(book.getId());
                c.setSectionId(s.getId());
                c.setOrdinal(chunkOrdinal++);
                c.setContent(text);
                sectionChunks.add(chunks.save(c));
            }
            chunksBySection.put(s.getId(), sectionChunks);
        }

        KnowledgeGraph graph = graphGeneration.generate(book, saved, chunksBySection);
        book.setStatus(BookStatus.READY);
        return new ImportResult(books.save(book), graph);
    }

    public Book require(Long id) {
        return books.findById(id).orElseThrow(() -> NotFoundException.of("书", id));
    }

    public Book view(AppUser actor, Long id) {
        Book book = require(id);
        access.checkCanSee(actor, book);
        return book;
    }

    public List<BookSection> sectionsOf(AppUser actor, Long bookId) {
        view(actor, bookId);
        return sections.findByBookIdOrderByOrdinal(bookId);
    }

    /** 我能看到的书：预置书 + 自己的 + 我的辅导者的（学习者）/ 名下学习者的（辅导者） */
    public List<Book> visibleTo(AppUser actor) {
        if (actor.getRole() == Role.OPS) {
            return books.findAll();
        }
        List<Long> owners = new ArrayList<>();
        owners.add(actor.getId());
        if (actor.getRole() == Role.LEARNER) {
            owners.addAll(users.tutorIdsOf(actor.getId()));
        } else {
            users.learnersOf(actor.getId()).forEach(u -> owners.add(u.getId()));
        }
        return books.findByVisibilityOrOwnerIdInOrderByIdDesc(Visibility.PUBLIC, owners).stream()
                .filter(b -> access.canSee(actor, b)).toList();
    }

    static FileFormat formatOf(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return FileFormat.MARKDOWN;
        }
        if (name.endsWith(".txt")) {
            return FileFormat.TXT;
        }
        if (name.endsWith(".epub")) {
            return FileFormat.EPUB;
        }
        if (name.endsWith(".pdf")) {
            return FileFormat.PDF;
        }
        if (name.endsWith(".docx")) {
            return FileFormat.DOCX;
        }
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".zip")) {
            return FileFormat.IMAGES;
        }
        throw new IllegalArgumentException("不认识的文件格式：" + filename);
    }
}
