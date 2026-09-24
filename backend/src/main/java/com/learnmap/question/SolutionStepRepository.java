package com.learnmap.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SolutionStepRepository extends JpaRepository<SolutionStep, Long> {

    List<SolutionStep> findBySolutionIdOrderByOrdinal(Long solutionId);
}
