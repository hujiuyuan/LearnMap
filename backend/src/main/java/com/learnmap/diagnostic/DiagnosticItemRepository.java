package com.learnmap.diagnostic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosticItemRepository extends JpaRepository<DiagnosticItem, Long> {

    List<DiagnosticItem> findBySessionIdOrderByRoundNoAscOrdinalAsc(Long sessionId);
}
