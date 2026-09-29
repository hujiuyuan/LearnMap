package com.learnmap.note;

import com.learnmap.common.CurrentUser;
import com.learnmap.note.NoteService.LinkSuggestion;
import com.learnmap.note.NoteService.MocDraft;
import com.learnmap.note.NoteService.NoteHabits;
import com.learnmap.note.NoteService.NoteRequest;
import com.learnmap.note.NoteService.NoteSummary;
import com.learnmap.note.NoteService.NoteView;
import com.learnmap.note.NoteService.SummaryFeedback;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final UserService users;

    @PostMapping("/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public NoteView create(@RequestHeader(CurrentUser.HEADER) Long userId, @RequestBody NoteRequest req) {
        return noteService.create(users.require(userId), req);
    }

    @PutMapping("/notes/{id}")
    public NoteView update(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id, @RequestBody NoteRequest req) {
        return noteService.update(users.require(userId), id, req);
    }

    @GetMapping("/notes/{id}")
    public NoteView get(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return noteService.get(users.require(userId), id);
    }

    @DeleteMapping("/notes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        noteService.delete(users.require(userId), id);
    }

    @GetMapping("/learners/{learnerId}/notes")
    public List<NoteSummary> list(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId,
                                  @RequestParam(required = false) NoteKind kind,
                                  @RequestParam(required = false) Long nodeId,
                                  @RequestParam(required = false) Long bookId) {
        return noteService.list(users.require(userId), learnerId, kind, nodeId, bookId);
    }

    @GetMapping("/learners/{learnerId}/note-habits")
    public NoteHabits habits(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long learnerId) {
        users.checkCanView(users.require(userId), learnerId);
        return noteService.habits(learnerId);
    }

    @PostMapping("/notes/{id}/feedback")
    public SummaryFeedback feedback(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return noteService.summaryFeedback(users.require(userId), id);
    }

    @GetMapping("/notes/{id}/link-suggestions")
    public List<LinkSuggestion> suggestions(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return noteService.linkSuggestions(users.require(userId), id);
    }

    @PostMapping("/notes/{id}/links/{targetId}")
    public NoteView link(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id, @PathVariable Long targetId,
                         @RequestParam(defaultValue = "false") boolean suggested) {
        return noteService.link(users.require(userId), id, targetId, suggested);
    }

    @DeleteMapping("/notes/{id}/links/{targetId}")
    public NoteView unlink(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id, @PathVariable Long targetId) {
        return noteService.unlink(users.require(userId), id, targetId);
    }

    @GetMapping("/graphs/{graphId}/moc-draft")
    public MocDraft mocDraft(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long graphId,
                             @RequestParam(required = false) Long sectionId) {
        return noteService.mocDraft(users.require(userId), graphId, sectionId);
    }
}
