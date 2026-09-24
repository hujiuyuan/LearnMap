package com.learnmap.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PomodoroSessionRepository extends JpaRepository<PomodoroSession, Long> {

    List<PomodoroSession> findByLearnerIdOrderByStartedAtDesc(Long learnerId);
}
