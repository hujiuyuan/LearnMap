package com.learnmap.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudyGoalRepository extends JpaRepository<StudyGoal, Long> {

    Optional<StudyGoal> findFirstByLearnerIdAndActiveTrueOrderByIdDesc(Long learnerId);

    List<StudyGoal> findByLearnerIdAndActiveTrue(Long learnerId);

    List<StudyGoal> findByActiveTrue();
}
