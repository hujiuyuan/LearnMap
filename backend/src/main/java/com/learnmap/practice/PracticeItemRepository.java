package com.learnmap.practice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeItemRepository extends JpaRepository<PracticeItem, Long> {

    List<PracticeItem> findBySessionIdOrderByIdAsc(Long sessionId);
}
