package com.learnmap.practice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    List<PracticeSession> findByLearnerIdOrderByIdDesc(Long learnerId);
}
