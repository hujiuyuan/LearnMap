package com.learnmap.book;

import com.learnmap.common.CurrentUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final UserService users;

    public record BookView(Long id, String title, String author, Long ownerId, Visibility visibility,
                           ReviewMode reviewMode, ContentKind contentKind, FileFormat fileFormat, BookStatus status) {
        static BookView of(Book b) {
            return new BookView(b.getId(), b.getTitle(), b.getAuthor(), b.getOwnerId(), b.getVisibility(),
                    b.getReviewMode(), b.getContentKind(), b.getFileFormat(), b.getStatus());
        }
    }

    public record SectionView(Long id, Long parentId, String title, int level, int ordinal) {
    }

    public record ImportView(BookView book, Long graphId, String graphStatus) {
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportView upload(@RequestHeader(CurrentUser.HEADER) Long userId,
                             @RequestPart("file") MultipartFile file,
                             @RequestParam String title,
                             @RequestParam(required = false) String author,
                             @RequestParam(required = false) ReviewMode reviewMode) throws IOException {
        BookService.ImportResult r = bookService.importBook(users.require(userId), title, author,
                file.getOriginalFilename(), file.getBytes(), reviewMode);
        return new ImportView(BookView.of(r.book()), r.graph().getId(), r.graph().getStatus().name());
    }

    @GetMapping
    public List<BookView> list(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return bookService.visibleTo(users.require(userId)).stream().map(BookView::of).toList();
    }

    @GetMapping("/{id}")
    public BookView get(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return BookView.of(bookService.view(users.require(userId), id));
    }

    @GetMapping("/{id}/sections")
    public List<SectionView> sections(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return bookService.sectionsOf(users.require(userId), id).stream()
                .map(s -> new SectionView(s.getId(), s.getParentId(), s.getTitle(), s.getLevel(), s.getOrdinal()))
                .toList();
    }
}
