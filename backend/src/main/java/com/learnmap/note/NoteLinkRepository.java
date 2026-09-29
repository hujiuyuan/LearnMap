package com.learnmap.note;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoteLinkRepository extends JpaRepository<NoteLink, Long> {

    List<NoteLink> findByFromNoteIdOrToNoteId(Long fromNoteId, Long toNoteId);

    List<NoteLink> findByFromNoteIdIn(Collection<Long> noteIds);

    Optional<NoteLink> findByFromNoteIdAndToNoteId(Long fromNoteId, Long toNoteId);
}
