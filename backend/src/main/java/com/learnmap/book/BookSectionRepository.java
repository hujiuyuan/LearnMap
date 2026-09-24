package com.learnmap.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookSectionRepository extends JpaRepository<BookSection, Long> {

    List<BookSection> findByBookIdOrderByOrdinal(Long bookId);
}
