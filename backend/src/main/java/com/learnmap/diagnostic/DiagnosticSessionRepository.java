package com.learnmap.diagnostic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiagnosticSessionRepository extends JpaRepository<DiagnosticSession, Long> {

    Optional<DiagnosticSession> findFirstByLearnerIdAndGraphIdAndStatusOrderByIdDesc(Long learnerId, Long graphId,
                                                                                  DiagnosticSession.Status status);
}
