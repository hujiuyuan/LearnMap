package com.learnmap.note;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NoteNodeRepository extends JpaRepository<NoteNode, Long> {

    List<NoteNode> findByNoteId(Long noteId);

    List<NoteNode> findByNoteIdIn(Collection<Long> noteIds);

    List<NoteNode> findByNodeId(Long nodeId);

    void deleteByNoteId(Long noteId);
}
