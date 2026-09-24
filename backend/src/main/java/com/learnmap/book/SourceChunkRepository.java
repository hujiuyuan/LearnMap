package com.learnmap.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceChunkRepository extends JpaRepository<SourceChunk, Long> {

    List<SourceChunk> findByBookIdOrderByOrdinal(Long bookId);

    List<SourceChunk> findBySectionIdOrderByOrdinal(Long sectionId);
}
