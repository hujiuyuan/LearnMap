package com.learnmap.note;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    List<Note> findByOwnerIdAndTitle(Long ownerId, String title);
}
